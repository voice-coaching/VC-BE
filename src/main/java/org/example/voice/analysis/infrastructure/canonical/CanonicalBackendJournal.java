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
        if(!settings.journalEnabled() || settings.journalStagingBudgetBytes()==0)fail(503,"NOT_READY");
        revision(workerRevision);revision(pipelineRevision);
        var request=contract.parse(raw,"analysisRequest");
        if(!RunPodContract.REQUEST_V2.equals(request.path("schemaVersion").asText())
                || analysis!=request.path("analysisId").asLong()
                || !execution.toString().equals(request.path("executionId").asText()))fail(422,"VALIDATION_FAILED");
        UUID requestId=UUID.fromString(request.path("requestId").asText());
        executions.requireActiveForVerification(analysis,requestId,execution,worker);
        var digest=jdbc.queryForObject("SELECT request_payload_sha256 FROM analysis_canonical_executions WHERE execution_id=?",String.class,execution);
        if(!contract.digest(request).equals(digest))fail(409,"PAYLOAD_DIGEST_MISMATCH");
        jdbc.update("""
                INSERT INTO analysis_canonical_journals
                    (execution_id,request_id,analysis_id,worker_instance_id,event_id,worker_revision,pipeline_revision,request_bytes,request_raw_sha256)
                VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT (execution_id) DO NOTHING
                """,execution,requestId,analysis,worker,UUID.randomUUID(),workerRevision,pipelineRevision,raw,sha(raw));
        var row=owned(analysis,execution,worker,false);
        if(!Arrays.equals(raw,(byte[])row.get("request_bytes")) || !workerRevision.equals(row.get("worker_revision"))
                || !pipelineRevision.equals(row.get("pipeline_revision")))fail(409,"PAYLOAD_DIGEST_MISMATCH");
        return snapshot(row);
    }

    @Transactional(timeout=5)
    public Snapshot status(long analysis,UUID execution,UUID worker) {return snapshot(owned(analysis,execution,worker,false));}

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public byte[] storedCallback(long analysis,UUID execution,UUID worker) {
        owned(analysis,execution,worker,false);
        var rows=jdbc.queryForList("SELECT callback_bytes FROM analysis_canonical_callback_inbox WHERE execution_id=?",execution);
        if(rows.isEmpty())fail(404,"UNKNOWN_EXECUTION");
        return (byte[])rows.getFirst().get("callback_bytes");
    }

    @Transactional(timeout=5)
    public void startProducer(long analysis,UUID execution,UUID worker) {
        var row=owned(analysis,execution,worker,true);
        if(row.get("producer_started_at")!=null)fail(409,"PRODUCER_OUTCOME_UNKNOWN");
        jdbc.update("UPDATE analysis_canonical_journals SET producer_started_at=CURRENT_TIMESTAMP WHERE execution_id=?",execution);
    }

    @Transactional(timeout=5)
    public void prepare(long analysis,UUID execution,UUID worker,byte[] raw) {
        var doc=contract.parse(raw,"journalPrepare");
        var row=owned(analysis,execution,worker,true);
        var a=doc.get("association");
        if(row.get("producer_started_at")==null)fail(409,"PRODUCER_NOT_STARTED");
        var registry=jdbc.queryForMap("SELECT recording_id,content_id FROM analysis_canonical_executions WHERE execution_id=?",execution);
        if(analysis!=a.path("analysisId").asLong() || !execution.toString().equals(a.path("executionId").asText())
                || !row.get("request_id").toString().equals(a.path("requestId").asText())
                || ((Number)registry.get("recording_id")).longValue()!=a.path("recordingId").asLong()
                || ((Number)registry.get("content_id")).longValue()!=a.path("contentId").asLong())fail(422,"VALIDATION_FAILED");
        if(row.get("prepare_bytes")!=null) {
            if(!Arrays.equals(raw,(byte[])row.get("prepare_bytes")))fail(409,"EVIDENCE_MANIFEST_CONFLICT");
            return;
        }
        // Serialize only bounded journal budget reservation, not network/inference.
        // Count declared bytes too: concurrent staging cannot oversubscribe the DB spool.
        if(settings.journalStagingBudgetBytes()==0)fail(503,"NOT_READY");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(193589719,35)");
        long requested=0;
        for(var meta:doc.get("artifacts"))requested+=meta.path("byteSize").asLong();
        long used=jdbc.queryForObject("SELECT COALESCE(SUM(byte_size),0) FROM analysis_canonical_upload_journal",Long.class);
        if(used>settings.journalStagingBudgetBytes()-requested)fail(503,"DEPENDENCY_UNAVAILABLE");
        var kinds=new HashSet<String>();
        for(var meta:doc.get("artifacts")) {
            String kind=meta.path("kind").asText();
            if(!kinds.add(kind) || (kind.equals("CORE") && !meta.path("sha256").equals(a.path("coreSha256"))))fail(422,"VALIDATION_FAILED");
            jdbc.update("""
                    INSERT INTO analysis_canonical_upload_journal (execution_id,kind,metadata_bytes,sha256,byte_size)
                    VALUES (?,?,?,?,?)
                    """,execution,kind,utf8(meta.toString()),meta.path("sha256").asText(),meta.path("byteSize").asInt());
        }
        jdbc.update("UPDATE analysis_canonical_journals SET prepare_bytes=? WHERE execution_id=?",raw,execution);
    }

    @Transactional(timeout=10)
    public void stage(long analysis,UUID execution,UUID worker,String kind,byte[] raw) {
        if(raw.length==0 || raw.length>ARTIFACT_LIMIT)fail(413,"PAYLOAD_TOO_LARGE");
        owned(analysis,execution,worker,true);
        var row=artifact(execution,kind,true);
        if(raw.length!=((Number)row.get("byte_size")).intValue() || !sha(raw).equals(row.get("sha256")))fail(422,"VALIDATION_FAILED");
        if(row.get("staged_bytes")!=null) {
            if(!Arrays.equals(raw,(byte[])row.get("staged_bytes")))fail(409,"EVIDENCE_MANIFEST_CONFLICT");
            return;
        }
        jdbc.update("UPDATE analysis_canonical_upload_journal SET staged_bytes=?,state='PREPARED' WHERE execution_id=? AND kind=? AND state='DECLARED'",
                raw,execution,kind);
    }

    @Transactional(timeout=5)
    public void beginUpload(long analysis,UUID execution,UUID worker,String kind) {
        owned(analysis,execution,worker,true);
        var row=artifact(execution,kind);
        if(!"PREPARED".equals(row.get("state")))fail(409,"EVIDENCE_UPLOAD_OUTCOME_UNKNOWN");
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM analysis_canonical_upload_journal WHERE execution_id=? AND state='DECLARED')",Boolean.class,execution)))
            fail(409,"EVIDENCE_STAGING_INCOMPLETE");
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
        if(!kind.equals(ref.path("kind").asText()) || !item.get("sha256").equals(ref.path("sha256").asText())
                || ((Number)item.get("byte_size")).longValue()!=ref.path("byteSize").asLong()
                || !contract.parse((byte[])item.get("metadata_bytes"),"journalMetadata").path("schemaVersion").equals(ref.path("schemaVersion"))
                || !settings.objectKey(analysis,execution,kind,(String)item.get("sha256")).equals(ref.path("objectKey").asText())
                || ref.path("versionId").asText().equals("null")
                || ref.path("versionId").asText().chars().anyMatch(c->c<32 || c==127))fail(422,"VALIDATION_FAILED");
        String state=(String)item.get("state");
        if(item.get("reference_bytes")!=null && !Arrays.equals(raw,(byte[])item.get("reference_bytes")))fail(409,"EVIDENCE_MANIFEST_CONFLICT");
        if(readback) {
            if(!Set.of("UPLOADED","VERIFIED").contains(state))fail(409,"EVIDENCE_MANIFEST_CONFLICT");
            if(state.equals("UPLOADED"))jdbc.update("UPDATE analysis_canonical_upload_journal SET state='VERIFIED' WHERE execution_id=? AND kind=?",execution,kind);
        } else if(state.equals("UPLOADING")) {
            jdbc.update("UPDATE analysis_canonical_upload_journal SET reference_bytes=?,state='UPLOADED' WHERE execution_id=? AND kind=?",raw,execution,kind);
        } else if(!Set.of("UPLOADED","VERIFIED").contains(state))fail(409,"EVIDENCE_MANIFEST_CONFLICT");
        // This is worker readback only, never the independent VERIFIED receipt.
    }

    @Transactional(timeout=5)
    public void manifest(long analysis,UUID execution,UUID worker,byte[] raw) {
        var manifest=contract.parse(raw,"evidenceManifest");
        var row=owned(analysis,execution,worker,true);
        if(row.get("prepare_bytes")==null || !manifest.path("association").equals(contract.parse((byte[])row.get("prepare_bytes"),"journalPrepare").path("association")))
            fail(409,"EVIDENCE_MANIFEST_CONFLICT");
        var references=new HashMap<String,JsonNode>();
        for(var item:manifest.path("artifacts"))if(references.put(item.path("kind").asText(),item)!=null)fail(422,"VALIDATION_FAILED");
        var items=jdbc.queryForList("SELECT kind,state,reference_bytes FROM analysis_canonical_upload_journal WHERE execution_id=?",execution);
        if(items.size()!=references.size())fail(409,"EVIDENCE_MANIFEST_CONFLICT");
        for(var item:items)if(!"VERIFIED".equals(item.get("state"))
                || !contract.parse((byte[])item.get("reference_bytes"),"journalReference").equals(references.get(item.get("kind"))))fail(409,"EVIDENCE_MANIFEST_CONFLICT");
        if(row.get("manifest_bytes")!=null) {
            if(!Arrays.equals(raw,(byte[])row.get("manifest_bytes")))fail(409,"EVIDENCE_MANIFEST_CONFLICT");
        } else jdbc.update("UPDATE analysis_canonical_journals SET manifest_bytes=? WHERE execution_id=?",raw,execution);
    }

    /** Called inside the already fenced inbox transaction. A prepared core cannot be hidden. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireCallback(CanonicalCallbackDocument doc) {
        var id=doc.identity();
        var row=immutableOwner(id.analysisId(),id.executionId(),id.workerId());
        if(!id.requestId().equals(row.get("request_id")) || !id.eventId().equals(row.get("event_id"))
                || !doc.workerRevision().equals(row.get("worker_revision")) || !doc.pipelineRevision().equals(row.get("pipeline_revision")))fail(409,"RESULT_EVENT_CONFLICT");
        if(doc.retention()==null) {
            if(row.get("prepare_bytes")!=null)fail(422,"VALIDATION_FAILED");
        } else if(row.get("manifest_bytes")==null || !sha((byte[])row.get("manifest_bytes")).equals(doc.retention().manifestSha256()))fail(422,"VALIDATION_FAILED");
    }

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
        if(!Set.of("APPLIED","DUPLICATE").contains(disposition))fail(422,"VALIDATION_FAILED");
        var id=doc.identity();
        var ack=new RunPodAnalysisResultCallbackResponseDto(id.eventId(),id.analysisId(),id.requestId(),id.executionId(),disposition,
                RunPodContract.timestamp(OffsetDateTime.now(ZoneOffset.UTC)));
        var raw=utf8(contract.encode(ack,"journalAck"));
        jdbc.update("INSERT INTO analysis_canonical_ack_journal(event_id,disposition,ack_bytes) VALUES (?,?,?) ON CONFLICT DO NOTHING",id.eventId(),disposition,raw);
        return jdbc.queryForObject("SELECT ack_bytes FROM analysis_canonical_ack_journal WHERE event_id=? AND disposition=?",byte[].class,id.eventId(),disposition);
    }

    private Map<String,Object> owned(long analysis,UUID execution,UUID worker,boolean active) {
        var row=immutableOwner(analysis,execution,worker);
        UUID request=(UUID)row.get("request_id");
        if(active)executions.requireActiveForVerification(analysis,request,execution,worker);
        else executions.requireVisibleForCallback(analysis,request,execution,worker);
        jdbc.queryForList("SELECT execution_id FROM analysis_canonical_journals WHERE execution_id=? FOR UPDATE",execution);
        // Re-read under the lock: upload checkpoint may have advanced while waiting.
        return immutableOwner(analysis,execution,worker);
    }
    private Map<String,Object> immutableOwner(long analysis,UUID execution,UUID worker) {
        var rows=jdbc.queryForList("SELECT * FROM analysis_canonical_journals WHERE execution_id=? AND analysis_id=?",execution,analysis);
        if(rows.size()!=1)fail(404,"UNKNOWN_EXECUTION");
        var row=rows.getFirst();
        if(!worker.equals(row.get("worker_instance_id")))fail(409,"WORKER_CONFLICT");
        return row;
    }
    private Map<String,Object> artifact(UUID execution,String kind) {
        return artifact(execution,kind,false);
    }
    private Map<String,Object> artifact(UUID execution,String kind,boolean includeRaw) {
        String columns=includeRaw?"*":"kind,metadata_bytes,sha256,byte_size,state,reference_bytes";
        var rows=jdbc.queryForList("SELECT "+columns+" FROM analysis_canonical_upload_journal WHERE execution_id=? AND kind=?",execution,kind);
        if(rows.size()!=1)fail(404,"TARGET_NOT_FOUND");
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
        if(value==null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:/+\\-]{0,99}"))fail(422,"VALIDATION_FAILED");
    }
    private static String sha(byte[] raw){return CanonicalCallbackDocument.sha256(raw);}
    private static byte[] utf8(String raw){return raw.getBytes(StandardCharsets.UTF_8);}
    private static void fail(int status,String reason){throw new RunPodContractException(status,reason);}
}
