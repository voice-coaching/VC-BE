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
import java.util.UUID;

/** Short transactional claims/fences. No B2, subprocess or public result writes here. */
@Component
public final class CanonicalEvidenceJobs {
    public record Task(UUID receiptId,long analysisId,UUID requestId,UUID executionId,UUID workerId,
                       UUID claimId,byte[] manifest,String requestDigest,OffsetDateTime deadline,int attempts) {}
    private final JdbcTemplate jdbc;
    private final AnalysisRequestOutboxJpaRepository outboxes;
    private final CanonicalEvidenceRegistrationService executions;
    private final RunPodContract contract;
    private final TransactionTemplate transaction;
    public CanonicalEvidenceJobs(JdbcTemplate jdbc,AnalysisRequestOutboxJpaRepository outboxes,
                                 CanonicalEvidenceRegistrationService executions,RunPodContract contract,
                                 PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.outboxes=outboxes;this.executions=executions;this.contract=contract;
        this.transaction=new TransactionTemplate(manager);
        this.transaction.setTimeout(5);
    }

    public Task claim() {
        // No row lock in the discovery query; all writers acquire analysis before receipt.
        var candidates=jdbc.queryForList("""
                SELECT receipt_id,analysis_id,request_id,execution_id,worker_instance_id
                FROM analysis_evidence_receipts
                WHERE (status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP)
                   OR (status='VERIFYING' AND verifier_claim_until<=CURRENT_TIMESTAMP)
                ORDER BY next_attempt_at,registered_at LIMIT 1
                """);
        if(candidates.isEmpty())return null;
        var candidate=candidates.getFirst();
        UUID id=(UUID)candidate.get("receipt_id"),request=(UUID)candidate.get("request_id"),
                execution=(UUID)candidate.get("execution_id"),worker=(UUID)candidate.get("worker_instance_id");
        long analysis=((Number)candidate.get("analysis_id")).longValue();
        return transaction.execute(tx -> {
            String inactive=null;
            try{}
            catch(RunPodContractException error){

                inactive=error.reason();
            }
            var rows=jdbc.queryForList("""
                    SELECT manifest_bytes,verification_attempts FROM analysis_evidence_receipts
                    WHERE receipt_id=? AND ((status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP)
                        OR (status='VERIFYING' AND verifier_claim_until<=CURRENT_TIMESTAMP)) FOR UPDATE
                    """,id);
            if(rows.isEmpty())return null;
            if(inactive!=null){terminate(id,inactive);return null;}
            int attempts=((Number)rows.getFirst().get("verification_attempts")).intValue();
            var identity=jdbc.queryForMap("SELECT request_payload_sha256,deadline_at FROM analysis_canonical_executions WHERE execution_id=?",execution);
            UUID claim=UUID.randomUUID();
            jdbc.update("""
                    UPDATE analysis_evidence_receipts SET status='VERIFYING',reason_code=NULL,
                        verification_attempts=verification_attempts+1,verifier_claim_id=?,
                        verifier_claim_until=CURRENT_TIMESTAMP+INTERVAL '8 minutes',updated_at=CURRENT_TIMESTAMP
                    WHERE receipt_id=?
                    """,claim,id);
            var deadline=jdbc.queryForObject("SELECT deadline_at FROM analysis_canonical_executions WHERE execution_id=?",
                    (row,index)->row.getObject(1,OffsetDateTime.class),execution);
            return new Task(id,analysis,request,execution,worker,claim,(byte[])rows.getFirst().get("manifest_bytes"),
                    (String)identity.get("request_payload_sha256"),deadline,attempts+1);
        });
    }

    public byte[] loadRequest(Task task) {
        // Load/decrypt after claim commit: a bad payload must consume a bounded
        // attempt/backoff rather than roll back claim and poison the queue head.
        return transaction.execute(tx -> {
            var outbox=outboxes.findByEventIdAndExecutionIdAndTransport(task.requestId().toString(),
                    task.executionId().toString(),"RUNPOD_HTTP")
                    .orElseThrow(()->new RunPodContractException(409,"UNKNOWN_EXECUTION"));

            String payload=outbox.getPayload();

            byte[] raw=payload.getBytes(StandardCharsets.UTF_8);
            var document=contract.parse(raw,"analysisRequest");

            return raw;
        });
    }

    public boolean active(Task task) {
        return Boolean.TRUE.equals(transaction.execute(tx -> {

            return owned(task);
        }));
    }

    public void finish(Task task,boolean valid,boolean retryable) {
        transaction.executeWithoutResult(tx -> {
            String inactive=null;
            try{}
            catch(RunPodContractException error){

                inactive=error.reason();
            }

            if(inactive!=null){terminate(task.receiptId(),inactive);return;}
            if(valid) {
                jdbc.update("""
                        UPDATE analysis_evidence_receipts SET status='VERIFIED',reason_code=NULL,
                            verified_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,
                            verifier_claim_id=NULL,verifier_claim_until=NULL
                        WHERE receipt_id=? AND verifier_claim_id=? AND status='VERIFYING'
                        """,task.receiptId(),task.claimId());
            } else {
                int delay=Math.min(30,1 << Math.min(task.attempts(),4));
                jdbc.update("""
                        UPDATE analysis_evidence_receipts SET status='PENDING',reason_code=NULL,
                            next_attempt_at=CURRENT_TIMESTAMP+(? * INTERVAL '1 second'),updated_at=CURRENT_TIMESTAMP,
                            verifier_claim_id=NULL,verifier_claim_until=NULL
                        WHERE receipt_id=? AND verifier_claim_id=? AND status='VERIFYING'
                        """,delay,task.receiptId(),task.claimId());
            }
        });
    }
    private boolean owned(Task task) { return true; }
    private void terminate(UUID id,String reason) {
        boolean expired="DEADLINE_EXCEEDED".equals(reason);
        jdbc.update("""
                UPDATE analysis_evidence_receipts SET status=?,reason_code=?,updated_at=CURRENT_TIMESTAMP,
                    verifier_claim_id=NULL,verifier_claim_until=NULL WHERE receipt_id=? AND status IN ('PENDING','VERIFYING')
                """,expired?"EXPIRED":"CANCELED",expired?"DEADLINE_EXCEEDED":"EXECUTION_INACTIVE",id);
    }
    private void reject(UUID id,String reason) {
        jdbc.update("""
                UPDATE analysis_evidence_receipts SET status='REJECTED',reason_code=?,updated_at=CURRENT_TIMESTAMP,
                    verifier_claim_id=NULL,verifier_claim_until=NULL WHERE receipt_id=? AND status IN ('PENDING','VERIFYING')
                """,reason,id);
    }
    private static boolean analysisEquals(Long actual,long expected){return actual!=null && actual==expected;}
}
