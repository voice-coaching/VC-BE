package org.example.voice.analysis.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.model.CanonicalExecutionBinding;
import org.example.voice.analysis.domain.port.CanonicalExecutionRegistry;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.exception.CanonicalExecutionRegistrationException;
import org.example.voice.analysis.exception.CanonicalExecutionRegistrationException.Reason;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CanonicalExecutionRegistryImpl implements CanonicalExecutionRegistry {
    private static final RowMapper<CanonicalExecutionBinding> MAPPER = (row, index) -> new CanonicalExecutionBinding(
            row.getObject("execution_id", UUID.class), row.getObject("request_id", UUID.class),
            row.getLong("analysis_id"), row.getLong("recording_id"), row.getLong("content_id"),
            row.getString("analysis_profile"), row.getString("request_schema_version"),
            row.getString("result_schema_version"), row.getString("request_payload_sha256"),
            row.getString("script_sha256"), row.getString("audio_sha256"),
            row.getObject("deadline_at", OffsetDateTime.class));

    private static final String VISIBLE_RELATION = """
            JOIN voice_recordings r ON r.id = a.recording_id
            JOIN training_sessions s ON s.id = r.training_session_id
            JOIN users u ON u.id = s.user_id
            JOIN practice_contents c ON c.id = s.content_id
            WHERE a.id = ? AND r.deleted_at IS NULL AND r.is_selected = TRUE
              AND s.status <> 'CANCELED' AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
              AND c.custom_deleted_at IS NULL
              AND (a.failure_code IS NULL OR a.failure_code NOT LIKE '%cancel%')
            """;

    private final AnalysisResultJpaRepository results;
    private final AnalysisRequestOutboxJpaRepository outboxes;
    private final JdbcTemplate jdbc;
    private final RunPodContract contract;

    @Override
    @Transactional
    public CanonicalExecutionBinding registerCurrent(Long analysisId, UUID requestId, UUID executionId) {
        Objects.requireNonNull(analysisId, "analysisId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(executionId, "executionId");
        // Same lock order as callback/retry/cancellation. Use the JPA outbox accessor for private payloads.
        var analysis = results.findForIngestion(analysisId)
                .orElseThrow(() -> failure(Reason.INACTIVE_EXECUTION));
        if (!analysis.isForActiveRequest(requestId) || !analysis.isForActiveExecution(executionId)
                || !analysis.isCanonicalExecution() || !RunPodContract.RESULT_V4.equals(analysis.getExpectedResultSchemaVersion())
                || (analysis.getStatus() != AnalysisStatus.PENDING && analysis.getStatus() != AnalysisStatus.PROCESSING)
                || !Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM analysis_results a " + VISIBLE_RELATION
                                + " AND s.status = 'ANALYZING')",
                        Boolean.class, analysisId))) {
            throw failure(Reason.INACTIVE_EXECUTION);
        }
        var candidate = outboxes.findByEventIdAndExecutionIdAndTransport(
                        requestId.toString(), executionId.toString(), "RUNPOD_HTTP")
                .orElseThrow(() -> failure(Reason.INVALID_STORED_REQUEST));
        var outbox = outboxes.findForDeliveryUpdate(candidate.getId())
                .orElseThrow(() -> failure(Reason.INVALID_STORED_REQUEST));
        if (!analysisId.equals(outbox.getAnalysisResult().getId())
                || outbox.getStatus() == AnalysisRequestOutboxStatus.FAILED) {
            throw failure(Reason.INACTIVE_EXECUTION);
        }
        String payload = outbox.getPayload();
        CanonicalExecutionBinding binding;
        try {
            var json = contract.parse(payload.getBytes(StandardCharsets.UTF_8), "analysisRequest");
            if (!RunPodContract.REQUEST_V2.equals(json.path("schemaVersion").asText())
                    || !requestId.toString().equals(json.path("requestId").asText())
                    || !executionId.toString().equals(json.path("executionId").asText())
                    || analysisId.longValue() != json.path("analysisId").longValue()
                    || analysis.getRecording().getId().longValue() != json.path("recordingId").longValue()
                    || analysis.getRecording().getTrainingSession().getContent().getId().longValue()
                            != json.path("contentId").longValue()
                    || !Objects.equals(analysis.getRecording().getAudioSha256(), json.path("audio").path("sha256").asText())) {
                throw failure(Reason.INVALID_STORED_REQUEST);
            }
            OffsetDateTime deadline = OffsetDateTime.parse(json.path("deadlineAt").asText());
            if (analysis.getExecutionDeadlineAt() == null || !deadline.isEqual(analysis.getExecutionDeadlineAt())
                    || !deadline.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
                throw failure(Reason.INACTIVE_EXECUTION);
            }
            binding = new CanonicalExecutionBinding(executionId, requestId, analysisId,
                    json.path("recordingId").longValue(), json.path("contentId").longValue(),
                    json.path("analysisProfile").asText(), json.path("schemaVersion").asText(),
                    json.path("resultSchemaVersion").asText(), contract.digest(payload),
                    json.path("scriptSha256").asText(), json.path("audio").path("sha256").asText(), deadline);
        } catch (RunPodContractException | IllegalArgumentException error) {
            throw failure(Reason.INVALID_STORED_REQUEST);
        }
        jdbc.update("""
                INSERT INTO analysis_canonical_executions
                    (execution_id, request_id, analysis_id, recording_id, content_id, analysis_profile,
                     request_schema_version, result_schema_version, request_payload_sha256,
                     script_sha256, audio_sha256, deadline_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, binding.executionId(), binding.requestId(), binding.analysisId(), binding.recordingId(),
                binding.contentId(), binding.analysisProfile(), binding.requestSchemaVersion(),
                binding.resultSchemaVersion(), binding.requestPayloadSha256(), binding.scriptSha256(),
                binding.audioSha256(), binding.deadlineAt());
        var stored = jdbc.query("SELECT * FROM analysis_canonical_executions WHERE execution_id = ?",
                        MAPPER, executionId).stream().findFirst()
                .orElseThrow(() -> failure(Reason.IMMUTABLE_BINDING_CONFLICT));
        if (!binding.equals(stored)) throw failure(Reason.IMMUTABLE_BINDING_CONFLICT);
        return stored;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CanonicalExecutionBinding> findCurrentForOwner(Long analysisId, Long userId) {
        // Retained history is never used as a fallback after retry, cancellation, or deletion.
        return jdbc.query("""
                SELECT e.* FROM analysis_canonical_executions e
                JOIN analysis_results a ON a.id = e.analysis_id
                  AND a.active_execution_id = e.execution_id::text
                  AND a.active_request_event_id = e.request_id::text
                  AND a.recording_id = e.recording_id
                """ + VISIBLE_RELATION + " AND s.user_id = ? AND s.content_id = e.content_id",
                MAPPER, analysisId, userId).stream().findFirst();
    }

    private static CanonicalExecutionRegistrationException failure(Reason reason) {
        return new CanonicalExecutionRegistrationException(reason);
    }
}
