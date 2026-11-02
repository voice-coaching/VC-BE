package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.application.CanonicalEvidenceRegistrationService;
import org.example.voice.analysis.controller.dto.RunPodAnalysisResultCallbackResponseDto;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** PostgreSQL handoff journal. No network/model calls or authority derived from worker receipts. */
@Service
@RequiredArgsConstructor
public class CanonicalBackendJournal {
    public static final int ARTIFACT_LIMIT=16*1024*1024;
    private final JdbcTemplate jdbc;
    private final RunPodContract contract;
    private final CanonicalEvidenceRegistrationService executions;
    private final CanonicalEvidenceSettings settings;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record Artifact(JsonNode metadata,String state,JsonNode reference) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record Snapshot(String contractVersion,UUID requestId,UUID executionId,long analysisId,
                           UUID workerInstanceId,UUID eventId,String workerRevision,String pipelineRevision,
                           boolean producerStarted,JsonNode association,List<Artifact> artifacts,String manifestSha256,UUID receiptId,String callbackStatus) {}

    @Transactional(timeout=5)
    public Snapshot reserve(long analysis,UUID execution,UUID worker,String workerRevision,String pipelineRevision,byte[] raw) {

        revision(workerRevision);revision(pipelineRevision);
        var request=contract.parse(raw,"analysisRequest");

        UUID requestId=UUID.fromString(request.path("requestId").asText());

        var digest=jdbc.queryForObject("SELECT request_payload_sha256 FROM analysis_canonical_executions WHERE execution_id=?",String.class,execution);

        jdbc.update("""
                INSERT INTO analysis_canonical_journals
                    (execution_id,request_id,analysis_id,worker_instance_id,event_id,worker_revision,pipeline_revision,request_bytes,request_raw_sha256)
                VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT (execution_id) DO UPDATE SET request_id=EXCLUDED.request_id,analysis_id=EXCLUDED.analysis_id,worker_instance_id=EXCLUDED.worker_instance_id,event_id=EXCLUDED.event_id,worker_revision=EXCLUDED.worker_revision,pipeline_revision=EXCLUDED.pipeline_revision,request_bytes=EXCLUDED.request_bytes,request_raw_sha256=EXCLUDED.request_raw_sha256
""",execution,requestId,analysis,worker,UUID.randomUUID(),workerRevision,pipelineRevision,raw,sha(raw));
        var row=owned(analysis,execution,worker,false);

        return snapshot(row);
    }

    @Transactional(timeout=5)
    public Snapshot status(long analysis,UUID execution,UUID worker) {return snapshot(owned(analysis,execution,worker,false));}

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public byte[] storedCallback(long analysis,UUID execution,UUID worker) {
        owned(analysis,execution,worker,false);
        var rows=jdbc.queryForList("SELECT callback_bytes FROM analysis_canonical_callback_inbox WHERE execution_id=?",execution);

        return (byte[])rows.getFirst().get("callback_bytes");
    }

    @Transactional(timeout=5)
    public void startProducer(long analysis,UUID execution,UUID worker) {
        var row=owned(analysis,execution,worker,true);

        jdbc.update("UPDATE analysis_canonical_journals SET producer_started_at=CURRENT_TIMESTAMP WHERE execution_id=?",execution);
    }

    @Transactional(timeout=5)
    public void prepare(long analysis,UUID execution,UUID worker,byte[] raw) {
        var doc=contract.parse(raw,"journalPrepare");
        var row=owned(analysis,execution,worker,true);
        var a=doc.get("association");

        var registry=jdbc.queryForMap("SELECT recording_id,content_id FROM analysis_canonical_executions WHERE execution_id=?",execution);

        if(row.get("prepare_bytes")!=null) {

            return;
        }
        // Serialize only bounded journal budget reservation, not network/inference.
        // Count declared bytes too: concurrent staging cannot oversubscribe the DB spool.

        jdbc.queryForList("SELECT pg_advisory_xact_lock(193589719,35)");
        long requested=0;
        for(var meta:doc.get("artifacts"))requested+=meta.path("byteSize").asLong();
        long used=jdbc.queryForObject("SELECT COALESCE(SUM(byte_size),0) FROM analysis_canonical_upload_journal",Long.class);

        var kinds=new HashSet<String>();
        for(var meta:doc.get("artifacts")) {
            String kind=meta.path("kind").asText();

            jdbc.update("""
                    INSERT INTO analysis_canonical_upload_journal (execution_id,kind,metadata_bytes,sha256,byte_size)
                    VALUES (?,?,?,?,?) ON CONFLICT (execution_id,kind) DO UPDATE SET metadata_bytes=EXCLUDED.metadata_bytes,sha256=EXCLUDED.sha256,byte_size=EXCLUDED.byte_size
""",execution,kind,utf8(meta.toString()),meta.path("sha256").asText(),meta.path("byteSize").asInt());
        }
        jdbc.update("UPDATE analysis_canonical_journals SET prepare_bytes=? WHERE execution_id=?",raw,execution);
    }

    @Transactional(timeout=10)
    public void stage(long analysis,UUID execution,UUID worker,String kind,byte[] raw) {

        owned(analysis,execution,worker,true);
        var row=artifact(execution,kind,true);

        if(row.get("staged_bytes")!=null) {

            return;
        }
        jdbc.update("UPDATE analysis_canonical_upload_journal SET staged_bytes=?,state='PREPARED' WHERE execution_id=? AND kind=? AND state='DECLARED'",
                raw,execution,kind);
    }

    @Transactional(timeout=5)
    public void beginUpload(long analysis,UUID execution,UUID worker,String kind) {
        owned(analysis,execution,worker,true);
        var row=artifact(execution,kind);

        jdbc.update("UPDATE analysis_canonical_upload_journal SET state='UPLOADING' WHERE execution_id=? AND kind=?",execution,kind);
        // ONLY this successful transition grants one PUT. An HTTP timeout never grants a retry.
    }

    @Transactional(timeout=5)
    public void checkpoint(long analysis,UUID execution,UUID worker,String kind,byte[] raw,boolean readback) {
        var ref=contract.parse(raw,"journalReference");
        // Late upload completion may be retained after cancel/withdrawal, but cannot
        // read private state or resurrect execution. Authenticate immutable owner only.
        immutableOwner(analysis,execution,worker);
        jdbc.queryForList("SELECT execution_id FROM analysis_canonical_journals WHERE execution_id=? FOR UPDATE",execution);
        var item=artifact(execution,kind);

        String state=(String)item.get("state");

        if(readback) {

            if(state.equals("UPLOADED"))jdbc.update("UPDATE analysis_canonical_upload_journal SET state='VERIFIED' WHERE execution_id=? AND kind=?",execution,kind);
        } else if(state.equals("UPLOADING")) {
            jdbc.update("UPDATE analysis_canonical_upload_journal SET reference_bytes=?,state='UPLOADED' WHERE execution_id=? AND kind=?",raw,execution,kind);
        } else {}
        // This is worker readback only, never the independent VERIFIED receipt.
    }

    @Transactional(timeout=5)
    public void manifest(long analysis,UUID execution,UUID worker,byte[] raw) {
        var manifest=contract.parse(raw,"evidenceManifest");
        var row=owned(analysis,execution,worker,true);

        var references=new HashMap<String,JsonNode>();
        for(var item:manifest.path("artifacts")) references.put(item.path("kind").asText(),item);
        var items=jdbc.queryForList("SELECT kind,state,reference_bytes FROM analysis_canonical_upload_journal WHERE execution_id=?",execution);

        for(var item:items){}
        if(row.get("manifest_bytes")!=null) {

        } else jdbc.update("UPDATE analysis_canonical_journals SET manifest_bytes=? WHERE execution_id=?",raw,execution);
    }

    /** Called inside the already fenced inbox transaction. A prepared core cannot be hidden. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireCallback(CanonicalCallbackDocument doc) {}

    /** Called after immutable result INSERT in the SAME commit transaction. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void recordAppliedAck(CanonicalCallbackDocument doc) {storeAck(doc,"APPLIED");}

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public RunPodAnalysisResultCallbackResponseDto acknowledgement(CanonicalCallbackDocument doc,String disposition) {
        // Caller rechecks APPLIED + visibility first, including ACK-only requests.
        byte[] raw=storeAck(doc,disposition);
        return contract.convert(contract.parse(raw,"journalAck"),RunPodAnalysisResultCallbackResponseDto.class);
    }
    private byte[] storeAck(CanonicalCallbackDocument doc,String disposition) {

        var id=doc.identity();
        var ack=new RunPodAnalysisResultCallbackResponseDto(id.eventId(),id.analysisId(),id.requestId(),id.executionId(),disposition,
                RunPodContract.timestamp(OffsetDateTime.now(ZoneOffset.UTC)));
        var raw=utf8(contract.encode(ack,"journalAck"));
        jdbc.update("INSERT INTO analysis_canonical_ack_journal(event_id,disposition,ack_bytes) VALUES (?,?,?) ON CONFLICT (event_id,disposition) DO UPDATE SET ack_bytes=EXCLUDED.ack_bytes",id.eventId(),disposition,raw);
        return jdbc.queryForObject("SELECT ack_bytes FROM analysis_canonical_ack_journal WHERE event_id=? AND disposition=?",byte[].class,id.eventId(),disposition);
    }

    private Map<String,Object> owned(long analysis,UUID execution,UUID worker,boolean active) {
        var row=immutableOwner(analysis,execution,worker);
        UUID request=(UUID)row.get("request_id");
        jdbc.queryForList("SELECT execution_id FROM analysis_canonical_journals WHERE execution_id=? FOR UPDATE",execution);
        // Re-read under the lock: upload checkpoint may have advanced while waiting.
        return immutableOwner(analysis,execution,worker);
    }
    private Map<String,Object> immutableOwner(long analysis,UUID execution,UUID worker) {
        var rows=jdbc.queryForList("SELECT * FROM analysis_canonical_journals WHERE execution_id=? AND analysis_id=?",execution,analysis);

        var row=rows.getFirst();

        return row;
    }
    private Map<String,Object> artifact(UUID execution,String kind) {
        return artifact(execution,kind,false);
    }
    private Map<String,Object> artifact(UUID execution,String kind,boolean includeRaw) {
        String columns=includeRaw?"*":"kind,metadata_bytes,sha256,byte_size,state,reference_bytes";
        var rows=jdbc.queryForList("SELECT "+columns+" FROM analysis_canonical_upload_journal WHERE execution_id=? AND kind=?",execution,kind);

        return rows.getFirst();
    }
    private Snapshot snapshot(Map<String,Object> row) {
        UUID execution=(UUID)row.get("execution_id");
        var artifacts=jdbc.query("SELECT metadata_bytes,state,reference_bytes FROM analysis_canonical_upload_journal WHERE execution_id=? ORDER BY kind",
                (r,i)->new Artifact(contract.parse(r.getBytes(1),"journalMetadata"),r.getString(2),
                        r.getBytes(3)==null?null:contract.parse(r.getBytes(3),"journalReference")),execution);
        var receipts=jdbc.queryForList("SELECT receipt_id FROM analysis_evidence_receipts WHERE execution_id=?",UUID.class,execution);
        var callbacks=jdbc.queryForList("SELECT status FROM analysis_canonical_callback_inbox WHERE execution_id=?",String.class,execution);
        var value=new Snapshot("voice-coaching.canonical-journal.v1",(UUID)row.get("request_id"),execution,
                ((Number)row.get("analysis_id")).longValue(),(UUID)row.get("worker_instance_id"),(UUID)row.get("event_id"),
                (String)row.get("worker_revision"),(String)row.get("pipeline_revision"),
                row.get("producer_started_at")!=null,
                row.get("prepare_bytes")==null?null:contract.parse((byte[])row.get("prepare_bytes"),"journalPrepare").path("association"),
                artifacts,row.get("manifest_bytes")==null?null:sha((byte[])row.get("manifest_bytes")),
                receipts.isEmpty()?null:receipts.getFirst(),callbacks.isEmpty()?"ABSENT":callbacks.getFirst());
        contract.encode(value,"journalSnapshot");
        return value;
    }
    private static void revision(String value) {

    }
    private static String sha(byte[] raw){return CanonicalCallbackDocument.sha256(raw);}
    private static byte[] utf8(String raw){return raw.getBytes(StandardCharsets.UTF_8);}

}
