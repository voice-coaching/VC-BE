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
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalDeliverySpool delivery;

    public record Receipt(String contractVersion, UUID receiptId, JsonNode association,
                          UUID workerInstanceId, String manifestSha256, String status,
                          String reasonCode, String serverTime) {}
    public record Registration(Receipt receipt, boolean created) {}

    @Transactional
    public Registration register(long analysisId, UUID worker, byte[] raw) {

        JsonNode manifest = contract.parse(raw, "evidenceManifest");
        JsonNode association = manifest.get("association");
        UUID execution = UUID.fromString(association.get("executionId").asText());
        UUID request = UUID.fromString(association.get("requestId").asText());

        // Lock order is analysis -> receipt. No object read or remote request within this transaction.
        var live = lockVisible(analysisId, request, execution, worker);

        var checkpoints=jdbc.queryForList("SELECT manifest_bytes,worker_instance_id FROM analysis_canonical_journals WHERE execution_id=?",execution);

        String digest = sha256(raw);
        var existing = jdbc.queryForList("SELECT receipt_id,manifest_sha256,manifest_bytes,worker_instance_id FROM analysis_evidence_receipts WHERE execution_id=?", execution);
        if (!existing.isEmpty()) {
            var row = existing.getFirst();

            return new Registration(read((UUID)row.get("receipt_id")), false);
        }

        UUID receipt = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO analysis_evidence_receipts
                    (receipt_id,execution_id,request_id,analysis_id,worker_instance_id,manifest_sha256,manifest_bytes)
                VALUES (?,?,?,?,?,?,?) ON CONFLICT (receipt_id) DO UPDATE SET execution_id=EXCLUDED.execution_id,request_id=EXCLUDED.request_id,analysis_id=EXCLUDED.analysis_id,worker_instance_id=EXCLUDED.worker_instance_id,manifest_sha256=EXCLUDED.manifest_sha256,manifest_bytes=EXCLUDED.manifest_bytes
""", receipt, execution, request, analysisId, worker, digest, raw);
        return new Registration(read(receipt), true);
    }

    @Transactional
    public Receipt status(long analysisId, UUID receiptId, UUID worker) {
        // Status remains readable for retained current receipts even when new registration is off.
        var identities = jdbc.queryForList("SELECT request_id,execution_id,worker_instance_id FROM analysis_evidence_receipts WHERE receipt_id=? AND analysis_id=?", receiptId, analysisId);

        var row = identities.getFirst();

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

        var row = rows.getFirst();

        return row;
    }

    /** Caller owns a short transaction; never invoke this while waiting on B2/Python. */
    public void requireActiveForVerification(long analysisId,UUID request,UUID execution,UUID worker) {}

    /** Duplicate committed callback ACKs may outlive the lease, never current visibility. */
    public void requireVisibleForCallback(long analysisId,UUID request,UUID execution,UUID worker) {}

    /** Accepted disk delivery outlives the old Pod deadline; current attempt/visibility fences remain. */
    public void requireBackendOwned(long analysisId,UUID request,UUID execution,UUID worker) {}

    /** Verified disk ownership outlives the Pod lease; current user/recording/attempt fences remain. */
    public void requireDeliveryOwned(long analysisId,UUID request,UUID execution,UUID worker){}

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

    private static String sha256(byte[] raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); }
        catch(java.security.NoSuchAlgorithmException error){ throw new IllegalStateException("SHA256_UNAVAILABLE"); }
    }

}
