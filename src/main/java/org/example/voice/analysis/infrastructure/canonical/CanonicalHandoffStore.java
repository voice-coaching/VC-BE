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
        if(!delivery.verified(doc.id(),doc.digest()))fail(409,"VERIFIED_DELIVERY_REQUIRED");
        return receiveLocked(analysis,worker,doc,true);
    }
    private Snapshot receiveLocked(long analysis,UUID worker,CanonicalHandoffDocument doc,boolean deferred) {
        var id=doc.projection().identity();
        if(analysis!=id.analysisId() || !worker.equals(id.workerId()))fail(409,"WORKER_CONFLICT");
        executions.requireVisibleForCallback(analysis,id.requestId(),id.executionId(),worker);
        var existing=jdbc.queryForList("SELECT handoff_id,handoff_sha256 FROM analysis_canonical_handoffs WHERE execution_id=?",id.executionId());
        if(!existing.isEmpty()) {
            if(!doc.id().equals(existing.getFirst().get("handoff_id")) || !doc.digest().equals(existing.getFirst().get("handoff_sha256")))fail(409,"RESULT_EVENT_CONFLICT");
            var current=owned(analysis,doc.id(),worker);
            if(!"STAGING".equals(current.get("state")))return snapshot(current);
            return resumeStaging(doc,current,deferred);
        } else {
            if(deferred)executions.requireDeliveryOwned(analysis,id.requestId(),id.executionId(),worker);
            else executions.requireActiveForVerification(analysis,id.requestId(),id.executionId(),worker);
            var registry=jdbc.queryForMap("SELECT * FROM analysis_canonical_executions WHERE execution_id=?",id.executionId());
            var journal=jdbc.queryForMap("SELECT * FROM analysis_canonical_journals WHERE execution_id=?",id.executionId());
            if(!doc.projection().schemaVersion().equals(registry.get("result_schema_version"))
                || ((Number)registry.get("recording_id")).longValue()!=id.recordingId()
                || ((Number)registry.get("content_id")).longValue()!=id.contentId()
                || !registry.get("request_payload_sha256").equals(doc.metadata().path("requestSha256").asText())
                || !id.eventId().equals(journal.get("event_id")) || !worker.equals(journal.get("worker_instance_id"))
                || !doc.projection().workerRevision().equals(journal.get("worker_revision"))
                || !doc.projection().pipelineRevision().equals(journal.get("pipeline_revision")))fail(409,"PAYLOAD_DIGEST_MISMATCH");
            settings.reserveAdmissionBudget(0);
            jdbc.update("""
                INSERT INTO analysis_canonical_handoffs(handoff_id,execution_id,event_id,request_id,analysis_id,
                worker_instance_id,metadata_bytes,projection_bytes,handoff_sha256,reserved_bytes) VALUES (?,?,?,?,?,?,?,?,?,?)
                """,doc.id(),id.executionId(),id.eventId(),id.requestId(),analysis,worker,doc.metadataBytes(),doc.projection().bytes(),doc.digest(),doc.reservedBytes());
            // One DB round trip for all immutable originals. Parsing/hash checks happened before this transaction.
            var values=new ArrayList<String>();var args=new ArrayList<Object>();
            boolean complete=true;
            for(var item:doc.metadata().get("artifacts")){
                String kind=item.path("kind").asText();byte[] raw=doc.inline().get(kind);
                if(raw!=null)CanonicalHandoffDocument.checkBytes(item,raw);else complete=false;
                values.add("(?,?,?,?,?,?)");Collections.addAll(args,doc.id(),kind,item.path("schemaVersion").asText(),item.path("sha256").asText(),item.path("byteSize").asInt(),raw);
            }
            if(!values.isEmpty())jdbc.update("INSERT INTO analysis_canonical_handoff_artifacts(handoff_id,kind,schema_version,sha256,byte_size,raw_bytes) VALUES "+String.join(",",values),args.toArray());
            if(complete)return sealLocked(analysis,doc.id(),id.executionId(),doc.digest());
            return new Snapshot(doc.id(),doc.digest(),"STAGING");
        }
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
                if(incoming.length!=((Number)row.get("byte_size")).intValue() || !CanonicalCallbackDocument.sha256(incoming).equals(row.get("sha256")))fail(422,"VALIDATION_FAILED");
                if(stored!=null && !Arrays.equals(incoming,stored))fail(409,"PAYLOAD_DIGEST_MISMATCH");
                if(stored==null) {values.add("(?::varchar(40),?::bytea)");Collections.addAll(args,kind,incoming);}
            } else if(stored==null)complete=false;
        }
        if(!expected.equals(present))fail(422,"VALIDATION_FAILED");
        if(!values.isEmpty() || complete) {
            long analysis=((Number)current.get("analysis_id")).longValue();
            if(deferred)executions.requireDeliveryOwned(analysis,(UUID)current.get("request_id"),(UUID)current.get("execution_id"),(UUID)current.get("worker_instance_id"));
            else active(current,false);
        }
        if(!values.isEmpty()) {
            args.add(doc.id());
            int changed=jdbc.update("UPDATE analysis_canonical_handoff_artifacts AS a SET raw_bytes=v.raw_bytes FROM (VALUES "
                +String.join(",",values)+") AS v(kind,raw_bytes) WHERE a.handoff_id=? AND a.kind=v.kind AND a.raw_bytes IS NULL",args.toArray());
            if(changed!=values.size())fail(409,"RESULT_EVENT_CONFLICT");
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
        if(items.size()!=1)fail(422,"VALIDATION_FAILED");
        var item=items.getFirst();
        if(raw.length!=((Number)item.get("byte_size")).intValue() || !CanonicalCallbackDocument.sha256(raw).equals(item.get("sha256")))fail(422,"VALIDATION_FAILED");
        if(item.get("raw_bytes")!=null) {
            if(!Arrays.equals(raw,(byte[])item.get("raw_bytes")))fail(409,"PAYLOAD_DIGEST_MISMATCH");
            return;
        }
        if(!"STAGING".equals(row.get("state")))fail(409,"RESULT_ALREADY_FINALIZED");
        active(row,false);
        jdbc.update("UPDATE analysis_canonical_handoff_artifacts SET raw_bytes=? WHERE handoff_id=? AND kind=? AND raw_bytes IS NULL",raw,handoff,kind);
    }
    @Transactional(timeout=5)
    public Snapshot seal(long analysis,UUID handoff,UUID worker) {
        jdbc.execute("SET LOCAL synchronous_commit = on");
        var row=owned(analysis,handoff,worker);
        if(!"STAGING".equals(row.get("state")))return snapshot(row);
        active(row,false);
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM analysis_canonical_handoff_artifacts WHERE handoff_id=? AND raw_bytes IS NULL)",Boolean.class,handoff)))fail(409,"EVIDENCE_STAGING_INCOMPLETE");
        jdbc.update("UPDATE analysis_canonical_handoffs SET state='RECEIVED',received_at=CURRENT_TIMESTAMP WHERE handoff_id=?",handoff);
        jdbc.update("UPDATE analysis_results SET handoff_received_at=CURRENT_TIMESTAMP WHERE id=? AND active_execution_id=?",analysis,row.get("execution_id").toString());
        return new Snapshot(handoff,(String)row.get("handoff_sha256"),"RECEIVED");
    }
    @Transactional(timeout=5)
    public Snapshot status(long analysis,UUID handoff,UUID worker){return snapshot(owned(analysis,handoff,worker));}
    private Map<String,Object> owned(long analysis,UUID handoff,UUID worker) {
        var rows=jdbc.queryForList("SELECT * FROM analysis_canonical_handoffs WHERE handoff_id=? AND analysis_id=?",handoff,analysis);
        if(rows.size()!=1)fail(404,"TARGET_NOT_FOUND");
        var row=rows.getFirst();
        executions.requireVisibleForCallback(analysis,(UUID)row.get("request_id"),(UUID)row.get("execution_id"),worker);
        if(!worker.equals(row.get("worker_instance_id")))fail(409,"WORKER_CONFLICT");
        return jdbc.queryForMap("SELECT * FROM analysis_canonical_handoffs WHERE handoff_id=? FOR UPDATE",handoff);
    }
    public void active(Map<String,Object> row,boolean backendOwned) {
        long analysis=((Number)row.get("analysis_id")).longValue();
        var request=(UUID)row.get("request_id");var execution=(UUID)row.get("execution_id");var worker=(UUID)row.get("worker_instance_id");
        if(backendOwned)executions.requireBackendOwned(analysis,request,execution,worker);
        else executions.requireActiveForVerification(analysis,request,execution,worker);
    }
    private static Snapshot snapshot(Map<String,Object> row){return new Snapshot((UUID)row.get("handoff_id"),(String)row.get("handoff_sha256"),(String)row.get("state"));}
    private static void fail(int status,String code){throw new RunPodContractException(status,code);}
}
