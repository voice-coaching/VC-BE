package org.example.voice.analysis.infrastructure.canonical;

import org.example.voice.analysis.application.CanonicalEvidenceRegistrationService;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/** Durable callback validation queue. No HTTP ACK, B2 I/O or subprocess inside transactions. */
@Component
public final class CanonicalCallbackInbox {
    public enum State { PENDING, VERIFIED, APPLIED }
    public record Task(UUID eventId, UUID requestId, UUID executionId, UUID workerId, long analysisId,
                       UUID claimId, byte[] callback, OffsetDateTime deadline, int attempts) {}
    public record Context(byte[] request, byte[] manifest) {}
    private final JdbcTemplate jdbc;
    private final CanonicalEvidenceRegistrationService executions;
    private final AnalysisRequestOutboxJpaRepository outboxes;
    private final RunPodContract contract;
    private final CanonicalEvidenceSettings settings;
    private final CanonicalBackendJournal journal;
    private final TransactionTemplate transaction;

    public CanonicalCallbackInbox(JdbcTemplate jdbc, CanonicalEvidenceRegistrationService executions,
                                  AnalysisRequestOutboxJpaRepository outboxes, RunPodContract contract,
                                  CanonicalEvidenceSettings settings, CanonicalBackendJournal journal, PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.executions=executions;this.outboxes=outboxes;this.contract=contract;this.settings=settings;
        transaction=new TransactionTemplate(manager);transaction.setTimeout(5);
        this.journal=journal;
    }

    public State register(CanonicalCallbackDocument doc) {
        if(!settings.callbackEnabled())fail(503,"NOT_READY");
        return transaction.execute(tx -> {
            var id=doc.identity();
            executions.requireVisibleForCallback(id.analysisId(),id.requestId(),id.executionId(),id.workerId());
            binding(doc);
            journal.requireCallback(doc);
            var rows=jdbc.queryForList("SELECT * FROM analysis_canonical_callback_inbox WHERE event_id=? OR execution_id=?",
                    id.eventId(),id.executionId());
            if(rows.isEmpty()) {
                active(id);
                receipt(doc);
                jdbc.update("""
                        INSERT INTO analysis_canonical_callback_inbox
                            (event_id,execution_id,request_id,analysis_id,worker_instance_id,evidence_receipt_id,
                             payload_sha256,raw_sha256,callback_bytes)
                        VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
                        """,id.eventId(),id.executionId(),id.requestId(),id.analysisId(),id.workerId(),
                        doc.retention()==null?null:doc.retention().receiptId(),doc.payloadSha256(),doc.rawSha256(),doc.bytes());
                rows=jdbc.queryForList("SELECT * FROM analysis_canonical_callback_inbox WHERE event_id=? OR execution_id=?",
                        id.eventId(),id.executionId());
            }
            if(rows.size()!=1)fail(409,"RESULT_EVENT_CONFLICT");
            var row=rows.getFirst();
            same(row,doc);
            String state=(String)row.get("status");
            if("APPLIED".equals(state)) {
                requireAppliedCurrent(doc);
                return State.APPLIED;
            }
            active(id);
            if("REJECTED".equals(state)) {
                boolean invalid="EVIDENCE_INVALID".equals(row.get("reason_code"));
                fail(invalid?422:503,invalid?"VALIDATION_FAILED":"DEPENDENCY_UNAVAILABLE");
            }
            if("CANCELED".equals(state) || "EXPIRED".equals(state))fail(409,"ANALYSIS_TERMINAL");
            return "VERIFIED".equals(state)?State.VERIFIED:State.PENDING;
        });
    }

    /** ACK loss/restart reconciliation only: never registers, verifies or applies a new result. */
    public void confirmApplied(CanonicalCallbackDocument doc) {
        transaction.executeWithoutResult(tx -> {
            var id=doc.identity();
            executions.requireVisibleForCallback(id.analysisId(),id.requestId(),id.executionId(),id.workerId());
            binding(doc);
            journal.requireCallback(doc);
            var rows=jdbc.queryForList("SELECT * FROM analysis_canonical_callback_inbox WHERE event_id=? OR execution_id=?",
                    id.eventId(),id.executionId());
            if(rows.isEmpty())fail(404,"UNKNOWN_EXECUTION");
            if(rows.size()!=1)fail(409,"RESULT_EVENT_CONFLICT");
            var row=rows.getFirst();
            same(row,doc);
            if(!"APPLIED".equals(row.get("status")) || row.get("verified_at")==null)
                fail(503,"DEPENDENCY_UNAVAILABLE");
            requireAppliedCurrent(doc);
        });
    }

    private void requireAppliedCurrent(CanonicalCallbackDocument doc) {
        var id=doc.identity();
        boolean current=Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM analysis_results a
                    JOIN analysis_canonical_results d ON d.event_id=a.canonical_result_event_id
                    WHERE a.id=? AND a.canonical_result_event_id=? AND a.last_result_event_id=?
                      AND a.last_result_payload_sha256=? AND a.status=?
                      AND d.analysis_id=a.id AND d.execution_id=? AND d.request_id=?
                      AND d.worker_instance_id=? AND d.raw_sha256=? AND d.payload_sha256=?
                      AND d.status=a.status)
                """,Boolean.class,id.analysisId(),id.eventId(),id.eventId().toString(),doc.payloadSha256(),doc.status().name(),
                id.executionId(),id.requestId(),id.workerId(),doc.rawSha256(),doc.payloadSha256()));
        if(!current)fail(409,"RESULT_ALREADY_FINALIZED");
    }

    public Task claim() {
        var candidates=jdbc.queryForList("""
                SELECT event_id,analysis_id,request_id,execution_id,worker_instance_id
                FROM analysis_canonical_callback_inbox
                WHERE (status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP)
                   OR (status='VERIFYING' AND claim_until<=CURRENT_TIMESTAMP)
                ORDER BY next_attempt_at,registered_at LIMIT 1
                """);
        if(candidates.isEmpty())return null;
        var candidate=candidates.getFirst();
        UUID event=(UUID)candidate.get("event_id"),request=(UUID)candidate.get("request_id"),
                execution=(UUID)candidate.get("execution_id"),worker=(UUID)candidate.get("worker_instance_id");
        long analysis=((Number)candidate.get("analysis_id")).longValue();
        return transaction.execute(tx -> {
            String inactive=inactive(analysis,request,execution,worker);
            var rows=jdbc.queryForList("""
                    SELECT callback_bytes,attempts FROM analysis_canonical_callback_inbox WHERE event_id=?
                        AND ((status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP)
                        OR (status='VERIFYING' AND claim_until<=CURRENT_TIMESTAMP)) FOR UPDATE
                    """,event);
            if(rows.isEmpty())return null;
            if(inactive!=null){terminate(event,inactive);return null;}
            int attempts=((Number)rows.getFirst().get("attempts")).intValue();
            if(attempts>=8){reject(event,"EVIDENCE_STORAGE_UNAVAILABLE");return null;}
            UUID claim=UUID.randomUUID();
            jdbc.update("""
                    UPDATE analysis_canonical_callback_inbox SET status='VERIFYING',claim_id=?,
                        claim_until=CURRENT_TIMESTAMP+INTERVAL '8 minutes',attempts=attempts+1,updated_at=CURRENT_TIMESTAMP
                    WHERE event_id=?
                    """,claim,event);
            var deadline=jdbc.queryForObject("SELECT deadline_at FROM analysis_canonical_executions WHERE execution_id=?",
                    (row,index)->row.getObject(1,OffsetDateTime.class),execution);
            return new Task(event,request,execution,worker,analysis,claim,(byte[])rows.getFirst().get("callback_bytes"),deadline,attempts+1);
        });
    }

    public Context load(Task task, CanonicalCallbackDocument doc) {
        return transaction.execute(tx -> {
            executions.requireActiveForVerification(task.analysisId(),task.requestId(),task.executionId(),task.workerId());
            if(!owned(task))fail(409,"STALE_EXECUTION");
            var bound=binding(doc);
            // JPA accessor decrypts private outbox payload; raw SQL can return the '{}' placeholder.
            var outbox=outboxes.findByEventIdAndExecutionIdAndTransport(task.requestId().toString(),task.executionId().toString(),"RUNPOD_HTTP")
                    .orElseThrow(()->new RunPodContractException(409,"UNKNOWN_EXECUTION"));
            if(!Long.valueOf(task.analysisId()).equals(outbox.getAnalysisResult().getId()))fail(422,"VALIDATION_FAILED");
            String payload=outbox.getPayload();
            if(!contract.digest(payload).equals(bound.get("request_payload_sha256")))fail(422,"VALIDATION_FAILED");
            byte[] raw=payload.getBytes(StandardCharsets.UTF_8);
            var request=contract.parse(raw,"analysisRequest");
            if(!RunPodContract.REQUEST_V2.equals(request.path("schemaVersion").asText())
                    || !task.executionId().toString().equals(request.path("executionId").asText())
                    || !task.requestId().toString().equals(request.path("requestId").asText())
                    || task.analysisId()!=request.path("analysisId").asLong())fail(422,"VALIDATION_FAILED");
            return new Context(raw,receipt(doc));
        });
    }

    public void fence(Task task) {
        transaction.executeWithoutResult(tx -> {
            executions.requireActiveForVerification(task.analysisId(),task.requestId(),task.executionId(),task.workerId());
            if(!owned(task))fail(409,"STALE_EXECUTION");
        });
    }

    public void finish(Task task,boolean valid,boolean retryable) {
        transaction.executeWithoutResult(tx -> {
            String inactive=inactive(task.analysisId(),task.requestId(),task.executionId(),task.workerId());
            if(!owned(task))return;
            if(inactive!=null){terminate(task.eventId(),inactive);return;}
            if(valid) {
                jdbc.update("""
                        UPDATE analysis_canonical_callback_inbox SET status='VERIFIED',reason_code=NULL,
                            verified_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,claim_id=NULL,claim_until=NULL
                        WHERE event_id=? AND claim_id=? AND status='VERIFYING'
                        """,task.eventId(),task.claimId());
                jdbc.update("INSERT INTO analysis_canonical_callback_apply(event_id) VALUES (?) ON CONFLICT DO NOTHING",task.eventId());
            } else if(retryable && task.attempts()<8) {
                jdbc.update("""
                        UPDATE analysis_canonical_callback_inbox SET status='PENDING',reason_code=NULL,
                            next_attempt_at=CURRENT_TIMESTAMP+(? * INTERVAL '1 second'),updated_at=CURRENT_TIMESTAMP,
                            claim_id=NULL,claim_until=NULL WHERE event_id=? AND claim_id=? AND status='VERIFYING'
                        """,Math.min(30,1 << Math.min(task.attempts(),4)),task.eventId(),task.claimId());
            } else reject(task.eventId(),retryable?"EVIDENCE_STORAGE_UNAVAILABLE":"EVIDENCE_INVALID");
        });
    }

    /** Caller must already own the analysis lock in the result-commit transaction. */
    public void requireVerifiedForCommit(CanonicalCallbackDocument doc) {
        active(doc.identity());
        binding(doc);
        receipt(doc);
        var rows=jdbc.queryForList("SELECT * FROM analysis_canonical_callback_inbox WHERE event_id=? FOR UPDATE",doc.identity().eventId());
        if(rows.size()!=1)fail(409,"UNKNOWN_EXECUTION");
        same(rows.getFirst(),doc);
        if(!"VERIFIED".equals(rows.getFirst().get("status")))fail(503,"DEPENDENCY_UNAVAILABLE");
    }
    public void markApplied(UUID event) {
        if(jdbc.update("""
                UPDATE analysis_canonical_callback_inbox SET status='APPLIED',updated_at=CURRENT_TIMESTAMP
                WHERE event_id=? AND status='VERIFIED'
                """,event)!=1)fail(409,"RESULT_ALREADY_FINALIZED");
    }

    private Map<String,Object> binding(CanonicalCallbackDocument doc) {
        var id=doc.identity();
        var rows=jdbc.queryForList("""
                SELECT e.* FROM analysis_canonical_executions e JOIN analysis_results a ON a.id=e.analysis_id
                JOIN voice_recordings r ON r.id=e.recording_id AND r.id=a.recording_id
                WHERE e.execution_id=? AND e.request_id=? AND e.analysis_id=? AND e.recording_id=? AND e.content_id=?
                    AND a.analysis_profile=e.analysis_profile AND a.expected_result_schema_version=e.result_schema_version
                    AND r.audio_sha256=e.audio_sha256
                """,id.executionId(),id.requestId(),id.analysisId(),id.recordingId(),id.contentId());
        if(rows.size()!=1)fail(409,"UNKNOWN_EXECUTION");
        var row=rows.getFirst();
        if(!doc.source().audioSha256().equals(row.get("audio_sha256"))
                || !doc.source().scriptSha256().equals(row.get("script_sha256")))fail(422,"VALIDATION_FAILED");
        return row;
    }
    private byte[] receipt(CanonicalCallbackDocument doc) {
        var id=doc.identity();
        if(doc.retention()==null) {
            // A previously registered core manifest cannot be hidden as a pre-core failure.
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM analysis_evidence_receipts WHERE execution_id=?)",
                    Boolean.class,id.executionId())))fail(422,"VALIDATION_FAILED");
            return null;
        }
        var rows=jdbc.queryForList("""
                SELECT manifest_bytes,manifest_sha256,status FROM analysis_evidence_receipts
                WHERE receipt_id=? AND execution_id=? AND request_id=? AND analysis_id=? AND worker_instance_id=?
                """,doc.retention().receiptId(),id.executionId(),id.requestId(),id.analysisId(),id.workerId());
        if(rows.size()!=1)fail(422,"VALIDATION_FAILED");
        var row=rows.getFirst();
        if(!"VERIFIED".equals(row.get("status")))fail(503,"DEPENDENCY_UNAVAILABLE");
        byte[] raw=(byte[])row.get("manifest_bytes");
        if(!doc.retention().manifestSha256().equals(row.get("manifest_sha256"))
                || !doc.retention().manifestSha256().equals(CanonicalCallbackDocument.sha256(raw)))fail(422,"VALIDATION_FAILED");
        return raw;
    }
    private void same(Map<String,Object> row,CanonicalCallbackDocument doc) {
        var id=doc.identity();
        if(!id.eventId().equals(row.get("event_id")))fail(409,"RESULT_ALREADY_FINALIZED");
        if(!id.executionId().equals(row.get("execution_id")) || !id.requestId().equals(row.get("request_id"))
                || id.analysisId()!=((Number)row.get("analysis_id")).longValue() || !id.workerId().equals(row.get("worker_instance_id"))
                || !doc.payloadSha256().equals(row.get("payload_sha256")) || !doc.rawSha256().equals(row.get("raw_sha256"))
                || !Arrays.equals(doc.bytes(),(byte[])row.get("callback_bytes")))fail(409,"RESULT_EVENT_CONFLICT");
    }
    private void active(CanonicalCallbackDocument.Identity id) {
        executions.requireActiveForVerification(id.analysisId(),id.requestId(),id.executionId(),id.workerId());
    }
    private String inactive(long analysis,UUID request,UUID execution,UUID worker) {
        try{executions.requireActiveForVerification(analysis,request,execution,worker);return null;}
        catch(RunPodContractException error){if(error.status()==404 || error.status()==409)return error.reason();throw error;}
    }
    private boolean owned(Task task) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM analysis_canonical_callback_inbox
                    WHERE event_id=? AND status='VERIFYING' AND claim_id=? AND claim_until>CURRENT_TIMESTAMP)
                """,Boolean.class,task.eventId(),task.claimId()));
    }
    private void terminate(UUID event,String reason) {
        boolean expired="DEADLINE_EXCEEDED".equals(reason);
        jdbc.update("""
                UPDATE analysis_canonical_callback_inbox SET status=?,reason_code=?,claim_id=NULL,claim_until=NULL,
                    verified_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE event_id=? AND status IN ('PENDING','VERIFYING','VERIFIED')
                """,expired?"EXPIRED":"CANCELED",expired?"DEADLINE_EXCEEDED":"EXECUTION_INACTIVE",event);
    }
    private void reject(UUID event,String reason) {
        jdbc.update("""
                UPDATE analysis_canonical_callback_inbox SET status='REJECTED',reason_code=?,claim_id=NULL,claim_until=NULL,
                    updated_at=CURRENT_TIMESTAMP WHERE event_id=? AND status IN ('PENDING','VERIFYING')
                """,reason,event);
    }
    private static void fail(int status,String reason) { throw new RunPodContractException(status,reason); }
}
