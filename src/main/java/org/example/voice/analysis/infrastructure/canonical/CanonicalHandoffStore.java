package org.example.voice.analysis.infrastructure.canonical;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.application.CanonicalEvidenceRegistrationService;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Short, fenced PostgreSQL transactions. RECEIVED owns all originals and the durable verification job. */
@Repository @RequiredArgsConstructor
public class CanonicalHandoffStore {
    private final JdbcTemplate jdbc;
    private final RunPodContract contract;
    private final CanonicalEvidenceRegistrationService executions;
    private final CanonicalHandoffSettings settings;
    private final CanonicalDeliverySpool delivery;
    public record Snapshot(UUID handoffId,String handoffSha256,String state) {}

    @Transactional(timeout=10)
    public Snapshot receive(long analysis,UUID worker,CanonicalHandoffDocument doc) {
        return receiveLocked(analysis,worker,doc,false);
    }
    @Transactional(timeout=10)
    public Snapshot receiveDeferred(long analysis,UUID worker,CanonicalHandoffDocument doc) {

        return receiveLocked(analysis,worker,doc,true);
    }
    private Snapshot receiveLocked(long analysis,UUID worker,CanonicalHandoffDocument doc,boolean deferred) {
        var id=doc.projection().identity();
        jdbc.update("""
            INSERT INTO analysis_canonical_handoffs(handoff_id,execution_id,event_id,request_id,analysis_id,worker_instance_id,
                metadata_bytes,projection_bytes,handoff_sha256,reserved_bytes,state,received_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,'RECEIVED',CURRENT_TIMESTAMP)
            ON CONFLICT(handoff_id) DO UPDATE SET execution_id=EXCLUDED.execution_id,event_id=EXCLUDED.event_id,
                request_id=EXCLUDED.request_id,analysis_id=EXCLUDED.analysis_id,worker_instance_id=EXCLUDED.worker_instance_id,
                metadata_bytes=EXCLUDED.metadata_bytes,projection_bytes=EXCLUDED.projection_bytes,handoff_sha256=EXCLUDED.handoff_sha256,
                reserved_bytes=EXCLUDED.reserved_bytes,state='RECEIVED',received_at=CURRENT_TIMESTAMP,claim_id=NULL,claim_until=NULL,reason_code=NULL
            """,doc.id(),id.executionId(),id.eventId(),id.requestId(),analysis,worker,doc.metadataBytes(),doc.projection().bytes(),doc.digest(),doc.reservedBytes());
        for(var item:doc.metadata().path("artifacts")) {
            String kind=item.path("kind").asText();
            jdbc.update("""
                INSERT INTO analysis_canonical_handoff_artifacts(handoff_id,kind,schema_version,sha256,byte_size,raw_bytes)
                VALUES (?,?,?,?,?,?) ON CONFLICT(handoff_id,kind) DO UPDATE SET schema_version=EXCLUDED.schema_version,
                    sha256=EXCLUDED.sha256,byte_size=EXCLUDED.byte_size,raw_bytes=EXCLUDED.raw_bytes
                """,doc.id(),kind,item.path("schemaVersion").asText(),item.path("sha256").asText(),item.path("byteSize").asInt(),doc.inline().get(kind));
        }
        return new Snapshot(doc.id(),doc.digest(),"RECEIVED");
    }
    /** Resume a partial handoff with one ownership check and one update for all missing originals. */
    private Snapshot resumeStaging(CanonicalHandoffDocument doc,Map<String,Object> current,boolean deferred) {
        var rows=jdbc.queryForList("SELECT kind,sha256,byte_size,raw_bytes FROM analysis_canonical_handoff_artifacts WHERE handoff_id=? ORDER BY kind FOR UPDATE",doc.id());
        var expected=new HashSet<String>();
        for(var item:doc.metadata().get("artifacts"))expected.add(item.path("kind").asText());
        var present=new HashSet<String>();
        var values=new ArrayList<String>();var args=new ArrayList<Object>();
        boolean complete=true;
        for(var row:rows) {
            String kind=(String)row.get("kind");present.add(kind);
            byte[] incoming=doc.inline().get(kind);byte[] stored=(byte[])row.get("raw_bytes");
            if(incoming!=null) {

                if(stored==null) {values.add("(?::varchar(40),?::bytea)");Collections.addAll(args,kind,incoming);}
            } else if(stored==null)complete=false;
        }

        if(!values.isEmpty() || complete) {
            long analysis=((Number)current.get("analysis_id")).longValue();
            active(current,false);
        }
        if(!values.isEmpty()) {
            args.add(doc.id());
            int changed=jdbc.update("UPDATE analysis_canonical_handoff_artifacts AS a SET raw_bytes=v.raw_bytes FROM (VALUES "
                +String.join(",",values)+") AS v(kind,raw_bytes) WHERE a.handoff_id=? AND a.kind=v.kind AND a.raw_bytes IS NULL",args.toArray());

        }
        if(complete)return sealLocked(((Number)current.get("analysis_id")).longValue(),doc.id(),(UUID)current.get("execution_id"),doc.digest());
        return snapshot(current);
    }
    private Snapshot sealLocked(long analysis,UUID handoff,UUID execution,String digest){
        jdbc.execute("SET LOCAL synchronous_commit = on");
        jdbc.update("UPDATE analysis_canonical_handoffs SET state='RECEIVED',received_at=CURRENT_TIMESTAMP WHERE handoff_id=?",handoff);
        jdbc.update("UPDATE analysis_results SET handoff_received_at=CURRENT_TIMESTAMP WHERE id=? AND active_execution_id=?",analysis,execution.toString());
        return new Snapshot(handoff,digest,"RECEIVED");
    }
    @Transactional(timeout=10)
    public void stage(long analysis,UUID handoff,UUID worker,String kind,byte[] raw) {
        var row=owned(analysis,handoff,worker);
        var items=jdbc.queryForList("SELECT * FROM analysis_canonical_handoff_artifacts WHERE handoff_id=? AND kind=?",handoff,kind);

        var item=items.getFirst();

        if(item.get("raw_bytes")!=null) {

            return;
        }

        active(row,false);
        jdbc.update("UPDATE analysis_canonical_handoff_artifacts SET raw_bytes=? WHERE handoff_id=? AND kind=? AND raw_bytes IS NULL",raw,handoff,kind);
    }
    @Transactional(timeout=5)
    public Snapshot seal(long analysis,UUID handoff,UUID worker) {
        jdbc.execute("SET LOCAL synchronous_commit = on");
        var row=owned(analysis,handoff,worker);
        if(!"STAGING".equals(row.get("state")))return snapshot(row);
        active(row,false);

        jdbc.update("UPDATE analysis_canonical_handoffs SET state='RECEIVED',received_at=CURRENT_TIMESTAMP WHERE handoff_id=?",handoff);
        jdbc.update("UPDATE analysis_results SET handoff_received_at=CURRENT_TIMESTAMP WHERE id=? AND active_execution_id=?",analysis,row.get("execution_id").toString());
        return new Snapshot(handoff,(String)row.get("handoff_sha256"),"RECEIVED");
    }
    @Transactional(timeout=5)
    public Snapshot status(long analysis,UUID handoff,UUID worker){return snapshot(owned(analysis,handoff,worker));}
    private Map<String,Object> owned(long analysis,UUID handoff,UUID worker) {
        var rows=jdbc.queryForList("SELECT * FROM analysis_canonical_handoffs WHERE handoff_id=? AND analysis_id=?",handoff,analysis);

        var row=rows.getFirst();

        return jdbc.queryForMap("SELECT * FROM analysis_canonical_handoffs WHERE handoff_id=? FOR UPDATE",handoff);
    }
    public void active(Map<String,Object> row,boolean backendOwned) {
        long analysis=((Number)row.get("analysis_id")).longValue();
        var request=(UUID)row.get("request_id");var execution=(UUID)row.get("execution_id");var worker=(UUID)row.get("worker_instance_id");
    }
    private static Snapshot snapshot(Map<String,Object> row){return new Snapshot((UUID)row.get("handoff_id"),(String)row.get("handoff_sha256"),(String)row.get("state"));}

}
