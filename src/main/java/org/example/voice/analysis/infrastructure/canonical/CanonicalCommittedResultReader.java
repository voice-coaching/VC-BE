package org.example.voice.analysis.infrastructure.canonical;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PRIVATE current-generation read, never a public response or a retained-history fallback.
 * Reads only committed, previously verified bytes; no B2/network/process work here.
 * Does not repeat inference or turn schema validity into a semantic-verification receipt.
 */
@Component
@RequiredArgsConstructor
public final class CanonicalCommittedResultReader {
    private final JdbcTemplate jdbc;
    private final RunPodContract contract;
    private record Stored(byte[] bytes, String rawSha, String payloadSha, UUID receiptId, String manifestSha) {}

    public Optional<CanonicalCallbackDocument> findCurrent(AnalysisResult result) {
        if (!result.isCanonicalExecution() || result.getCanonicalResultEventId() == null) return Optional.empty();
        var recording = result.getRecording();
        var session = recording.getTrainingSession();
        var rows = jdbc.query("""
                SELECT d.callback_bytes,d.raw_sha256,d.payload_sha256,d.evidence_receipt_id,q.manifest_sha256
                FROM analysis_results a
                JOIN voice_recordings r ON r.id=a.recording_id
                JOIN training_sessions s ON s.id=r.training_session_id
                JOIN users u ON u.id=s.user_id JOIN practice_contents c ON c.id=s.content_id
                JOIN analysis_canonical_results d ON d.event_id=a.canonical_result_event_id
                    AND d.analysis_id=a.id AND d.recording_id=r.id AND d.content_id=c.id
                    AND d.execution_id::text=a.active_execution_id AND d.request_id::text=a.active_request_event_id
                    AND d.event_id::text=a.last_result_event_id AND d.payload_sha256=a.last_result_payload_sha256
                    AND d.status=a.status AND d.analysis_profile=a.analysis_profile
                    AND d.schema_version=a.committed_result_schema_version
                JOIN analysis_canonical_executions e ON e.execution_id=d.execution_id
                    AND e.request_id=d.request_id AND e.analysis_id=d.analysis_id
                    AND e.recording_id=d.recording_id AND e.content_id=d.content_id
                    AND e.result_schema_version=d.schema_version AND e.analysis_profile=d.analysis_profile
                    AND e.audio_sha256=r.audio_sha256
                LEFT JOIN analysis_canonical_callback_inbox i ON i.event_id=d.event_id AND i.status='APPLIED'
                    AND i.verified_at IS NOT NULL AND i.execution_id=d.execution_id AND i.request_id=d.request_id
                    AND i.analysis_id=d.analysis_id AND i.worker_instance_id=d.worker_instance_id
                    AND i.raw_sha256=d.raw_sha256 AND i.payload_sha256=d.payload_sha256
                    AND i.evidence_receipt_id IS NOT DISTINCT FROM d.evidence_receipt_id
                LEFT JOIN analysis_canonical_handoffs h ON h.handoff_id=d.handoff_id AND h.state='COMMITTED'
                    AND h.verified_at IS NOT NULL AND h.event_id=d.event_id AND h.execution_id=d.execution_id
                    AND h.request_id=d.request_id AND h.analysis_id=d.analysis_id AND h.worker_instance_id=d.worker_instance_id
                    AND h.projection_bytes=d.callback_bytes
                LEFT JOIN analysis_evidence_receipts q ON q.receipt_id=d.evidence_receipt_id
                WHERE a.id=? AND u.id=? AND a.canonical_result_event_id=?
                    AND a.expected_result_schema_version=d.schema_version
                    AND ((d.schema_version='voice-coaching.runpod-analysis-result.v4'
                          AND d.handoff_id IS NULL AND i.event_id IS NOT NULL)
                      OR (d.schema_version='voice-coaching.runpod-analysis-result.v5'
                          AND d.handoff_id IS NOT NULL AND h.handoff_id IS NOT NULL))
                    AND d.worker_instance_id::text=a.worker_instance_id
                    AND r.deleted_at IS NULL AND r.is_selected=TRUE AND c.custom_deleted_at IS NULL
                    AND u.status='ACTIVE' AND u.deleted_at IS NULL AND s.status<>'CANCELED'
                    AND (a.failure_code IS NULL OR a.failure_code NOT LIKE '%cancel%')
                    AND (d.evidence_receipt_id IS NULL OR
                        (q.status='VERIFIED' AND q.verified_at IS NOT NULL AND q.execution_id=d.execution_id
                         AND q.request_id=d.request_id AND q.analysis_id=d.analysis_id
                         AND q.worker_instance_id=d.worker_instance_id))
                """, (row, index) -> new Stored(row.getBytes("callback_bytes"),row.getString("raw_sha256"),
                    row.getString("payload_sha256"),row.getObject("evidence_receipt_id",UUID.class),
                    row.getString("manifest_sha256")), result.getId(),session.getUserId(),result.getCanonicalResultEventId());
        if (rows.isEmpty()) return Optional.empty();
        var stored = rows.getFirst();
        CanonicalCallbackDocument document;
        try {
            document = CanonicalCallbackDocument.parse(stored.bytes(),contract);
        } catch (org.example.voice.analysis.infrastructure.runpod.RunPodContractException | IllegalArgumentException error) {
            throw unavailable();
        }
        var identity = document.identity();
        if (!stored.rawSha().equals(document.rawSha256()) || !stored.payloadSha().equals(document.payloadSha256())
                || !stored.payloadSha().equals(result.getLastResultPayloadSha256())
                || !result.getCanonicalResultEventId().equals(identity.eventId())
                || !identity.eventId().toString().equals(result.getLastResultEventId())
                || !result.isForActiveRequest(identity.requestId()) || !result.isForActiveExecution(identity.executionId())
                || !identity.workerId().toString().equals(result.getWorkerInstanceId())
                || identity.analysisId()!=result.getId() || identity.recordingId()!=recording.getId()
                || identity.contentId()!=session.getContent().getId() || document.status()!=result.getStatus()
                || !document.schemaVersion().equals(result.getExpectedResultSchemaVersion())
                || !document.schemaVersion().equals(result.getCommittedResultSchemaVersion())
                || !Objects.equals(document.source().audioSha256(),recording.getAudioSha256())
                || !Objects.equals(document.retention()==null?null:document.retention().receiptId(),stored.receiptId())
                || !Objects.equals(document.retention()==null?null:document.retention().manifestSha256(),stored.manifestSha())) {
            throw unavailable();
        }
        return Optional.of(document);
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("CANONICAL_RESULT_UNAVAILABLE");
    }
}
