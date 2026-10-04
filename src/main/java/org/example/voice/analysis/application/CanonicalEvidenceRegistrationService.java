package org.example.voice.analysis.application;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.infrastructure.canonical.CanonicalEvidenceSettings;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

/** Registration commits immutable bytes + a verification job, never VERIFIED. No B2 I/O here. */
@Service
@RequiredArgsConstructor
public class CanonicalEvidenceRegistrationService {
    private final JdbcTemplate jdbc;
    private final RunPodContract contract;
    private final CanonicalEvidenceSettings settings;

    public record Receipt(String contractVersion, UUID receiptId, JsonNode association,
                          UUID workerInstanceId, String manifestSha256, String status,
                          String reasonCode, String serverTime) {}
    public record Registration(Receipt receipt, boolean created) {}

    @Transactional
    public Registration register(long analysisId, UUID worker, byte[] raw) {
        if (!settings.registrationEnabled() || !settings.configured()) fail(503, "NOT_READY");
        JsonNode manifest = contract.parse(raw, "evidenceManifest");
        JsonNode association = manifest.get("association");
        UUID execution = UUID.fromString(association.get("executionId").asText());
        UUID request = UUID.fromString(association.get("requestId").asText());
        if (association.get("analysisId").longValue() != analysisId) fail(422, "VALIDATION_FAILED");
        // Lock order is analysis -> receipt. No object read or remote request within this transaction.
        var live = lockVisible(analysisId, request, execution, worker);
        validateManifest(manifest, live, analysisId, execution);
        var checkpoints=jdbc.queryForList("SELECT manifest_bytes,worker_instance_id FROM analysis_canonical_journals WHERE execution_id=?",execution);
        if(checkpoints.size()!=1 || !worker.equals(checkpoints.getFirst().get("worker_instance_id"))
                || !Arrays.equals(raw,(byte[])checkpoints.getFirst().get("manifest_bytes")))fail(409,"EVIDENCE_MANIFEST_CONFLICT");
        String digest = sha256(raw);
        var existing = jdbc.queryForList("SELECT receipt_id,manifest_sha256,manifest_bytes,worker_instance_id FROM analysis_evidence_receipts WHERE execution_id=?", execution);
        if (!existing.isEmpty()) {
            var row = existing.getFirst();
            if (!digest.equals(row.get("manifest_sha256")) || !Arrays.equals(raw, (byte[])row.get("manifest_bytes"))
                    || !worker.equals(row.get("worker_instance_id"))) fail(409, "EVIDENCE_MANIFEST_CONFLICT");
            return new Registration(read((UUID)row.get("receipt_id")), false);
        }
        requireActive(live);
        UUID receipt = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO analysis_evidence_receipts
                    (receipt_id,execution_id,request_id,analysis_id,worker_instance_id,manifest_sha256,manifest_bytes)
                VALUES (?,?,?,?,?,?,?)
                """, receipt, execution, request, analysisId, worker, digest, raw);
        return new Registration(read(receipt), true);
    }

    @Transactional
    public Receipt status(long analysisId, UUID receiptId, UUID worker) {
        // Status remains readable for retained current receipts even when new registration is off.
        var identities = jdbc.queryForList("SELECT request_id,execution_id,worker_instance_id FROM analysis_evidence_receipts WHERE receipt_id=? AND analysis_id=?", receiptId, analysisId);
        if (identities.isEmpty()) throw new RunPodContractException(404, "TARGET_NOT_FOUND");
        var row = identities.getFirst();
        if (!worker.equals(row.get("worker_instance_id"))) fail(409, "WORKER_CONFLICT");
        var live = lockVisible(analysisId, (UUID)row.get("request_id"), (UUID)row.get("execution_id"), worker);
        // A terminal receipt is immutable. Live execution/visibility is always checked separately.
        String reason = inactiveReason(live);
        if (reason != null) {
            String state = "DEADLINE_EXCEEDED".equals(reason) ? "EXPIRED" : "CANCELED";
            jdbc.update("""
                    UPDATE analysis_evidence_receipts SET status=?,reason_code=?,updated_at=CURRENT_TIMESTAMP,
                        verifier_claim_id=NULL,verifier_claim_until=NULL
                    WHERE receipt_id=? AND status IN ('PENDING','VERIFYING')
                    """, state, state.equals("EXPIRED") ? "DEADLINE_EXCEEDED" : "EXECUTION_INACTIVE", receiptId);
        }
        return read(receiptId);
    }

    private java.util.Map<String,Object> lockVisible(long analysisId, UUID request, UUID execution, UUID worker) {
        // Lock the analysis before checking the mutable ownership joins; retry/cancel use this lock too.
        var locks = jdbc.queryForList("SELECT id FROM analysis_results WHERE id=? FOR UPDATE", analysisId);
        if (locks.isEmpty()) throw new RunPodContractException(404, "TARGET_NOT_FOUND");
        var rows = jdbc.queryForList("""
                SELECT a.active_request_event_id,a.active_execution_id,a.worker_instance_id,a.claim_expires_at,
                       a.status,a.execution_deadline_at,e.recording_id,e.content_id,e.deadline_at
                FROM analysis_results a
                JOIN analysis_canonical_executions e ON e.analysis_id=a.id AND e.execution_id=? AND e.request_id=?
                JOIN voice_recordings r ON r.id=a.recording_id AND r.id=e.recording_id
                JOIN training_sessions s ON s.id=r.training_session_id AND s.content_id=e.content_id
                JOIN users u ON u.id=s.user_id
                JOIN practice_contents c ON c.id=s.content_id
                WHERE a.id=? AND r.deleted_at IS NULL AND r.is_selected=TRUE
                  AND s.status<>'CANCELED' AND u.status='ACTIVE' AND u.deleted_at IS NULL
                  AND c.custom_deleted_at IS NULL
                  AND (a.failure_code IS NULL OR a.failure_code NOT LIKE '%cancel%')
                """, execution, request, analysisId);
        if (rows.isEmpty()) throw new RunPodContractException(409, "ANALYSIS_CANCELLED");
        var row = rows.getFirst();
        if (!request.toString().equals(row.get("active_request_event_id"))
                || !execution.toString().equals(row.get("active_execution_id"))) fail(409, "STALE_EXECUTION");
        if (row.get("worker_instance_id") == null) fail(409, "CLAIM_REQUIRED");
        if (!worker.toString().equals(row.get("worker_instance_id"))) fail(409, "WORKER_CONFLICT");
        return row;
    }

    /** Caller owns a short transaction; never invoke this while waiting on B2/Python. */
    public void requireActiveForVerification(long analysisId,UUID request,UUID execution,UUID worker) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("VERIFICATION_TRANSACTION_REQUIRED");
        requireActive(lockVisible(analysisId,request,execution,worker));
    }

    /** Duplicate committed callback ACKs may outlive the lease, never current visibility. */
    public void requireVisibleForCallback(long analysisId,UUID request,UUID execution,UUID worker) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("CALLBACK_TRANSACTION_REQUIRED");
        lockVisible(analysisId,request,execution,worker);
    }

    /** Backend ownership ignores the old Pod heartbeat, never the original deadline or current attempt. */
    public void requireBackendOwned(long analysisId,UUID request,UUID execution,UUID worker) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("VERIFICATION_TRANSACTION_REQUIRED");
        var live=lockVisible(analysisId,request,execution,worker);
        var now=java.time.Instant.now();
        if(!instant(live.get("deadline_at")).isAfter(now) || !instant(live.get("execution_deadline_at")).isAfter(now))fail(409,"DEADLINE_EXCEEDED");
        if(!java.util.Set.of("PENDING","PROCESSING").contains(live.get("status")))fail(409,"ANALYSIS_TERMINAL");
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM analysis_canonical_handoffs WHERE execution_id=? AND received_at IS NOT NULL AND state IN ('RECEIVED','VERIFYING'))",Boolean.class,execution)))fail(409,"HANDOFF_REQUIRED");
    }

    private void validateManifest(JsonNode manifest, java.util.Map<String,Object> live, long analysisId, UUID execution) {
        var association = manifest.get("association");
        if (association.get("recordingId").longValue() != ((Number)live.get("recording_id")).longValue()
                || association.get("contentId").longValue() != ((Number)live.get("content_id")).longValue()) fail(422, "VALIDATION_FAILED");
        var kinds = new java.util.HashSet<String>();
        for (var artifact : manifest.get("artifacts")) {
            String kind=artifact.get("kind").asText(), sha=artifact.get("sha256").asText();
            String version=artifact.get("versionId").asText();
            if (!kinds.add(kind) || !settings.objectKey(analysisId,execution,kind,sha).equals(artifact.get("objectKey").asText())
                    || "null".equals(version) || version.chars().anyMatch(c -> c < 32 || c == 127)
                    || ("CORE".equals(kind) && !sha.equals(association.get("coreSha256").asText()))) fail(422, "VALIDATION_FAILED");
        }
    }

    private Receipt read(UUID id) {
        return jdbc.queryForObject("SELECT * FROM analysis_evidence_receipts WHERE receipt_id=?", (row, index) -> {
            var association = contract.parse(row.getBytes("manifest_bytes"), "evidenceManifest").get("association");
            var receipt = new Receipt(RunPodContract.CAPABILITY_VERSION, id, association,
                    row.getObject("worker_instance_id",UUID.class), row.getString("manifest_sha256"),
                    row.getString("status"), row.getString("reason_code"),RunPodContract.timestamp(OffsetDateTime.now(ZoneOffset.UTC)));
            contract.encode(receipt,"evidenceReceipt");
            return receipt;
        }, id);
    }

    private static java.time.Instant instant(Object value) {
        if (value instanceof OffsetDateTime date) return date.toInstant();
        if (value instanceof java.sql.Timestamp date) return date.toInstant();
        throw new RunPodContractException(503,"DEPENDENCY_UNAVAILABLE");
    }
    private static String inactiveReason(java.util.Map<String,Object> live) {
        var now=java.time.Instant.now();
        if (!instant(live.get("deadline_at")).isAfter(now)
                || !instant(live.get("execution_deadline_at")).isAfter(now)) return "DEADLINE_EXCEEDED";
        if (!java.util.Set.of("PENDING","PROCESSING").contains(live.get("status"))) return "ANALYSIS_TERMINAL";
        if (live.get("claim_expires_at")==null || !instant(live.get("claim_expires_at")).isAfter(now)) return "LEASE_EXPIRED";
        return null;
    }
    private static void requireActive(java.util.Map<String,Object> live) {
        String reason=inactiveReason(live);
        if(reason!=null) fail(409,reason);
    }
    private static String sha256(byte[] raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); }
        catch(java.security.NoSuchAlgorithmException error){ throw new IllegalStateException("SHA256_UNAVAILABLE"); }
    }
    private static void fail(int status,String reason){throw new RunPodContractException(status,reason);}
}
