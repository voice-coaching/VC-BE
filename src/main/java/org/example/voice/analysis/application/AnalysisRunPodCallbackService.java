package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.*;
import org.example.voice.analysis.domain.port.AnalysisResultReader;
import org.example.voice.analysis.domain.port.AnalysisResultWriter;
import org.example.voice.analysis.domain.type.AnalysisResultIngestionDisposition;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalysisRunPodCallbackService {
    private final AnalysisResultReader analysisResultReader;
    private final AnalysisResultWriter analysisResultWriter;
    private final AnalysisResultIngestionService ingestionService;
    private final RunPodAnalysisProperties properties;
    private final AnalysisRequestOutboxJpaRepository outboxRepository;
    private final RunPodContract contract;

    @Transactional
    public AnalysisRunPodControlData claim(Long analysisId, AnalysisRunPodClaimCommand request) {
        AnalysisResult result = find(analysisId);
        OffsetDateTime now = now();
        requireActive(result, request.requestId(), request.executionId());
        String payload = executionPayload(result, request.requestId(), request.executionId());
        if (!contract.digest(payload).equals(request.requestPayloadSha256())) fail(409, "PAYLOAD_DIGEST_MISMATCH");
        OffsetDateTime deadline = deadline(payload, now);
        requireOwner(result, request.workerInstanceId(), now, false);
        if (!result.claim(request.requestId(), request.executionId(), request.workerInstanceId(), cappedLease(now, deadline))) {
            fail(409, "STALE_EXECUTION");
        }
        analysisResultWriter.save(result);
        return control(result, request.requestId(), request.executionId(), now);
    }

    @Transactional
    public AnalysisRunPodControlData heartbeat(Long analysisId, AnalysisRunPodHeartbeatCommand request) {
        AnalysisResult result = find(analysisId);
        OffsetDateTime now = now();
        requireActive(result, request.requestId(), request.executionId());
        OffsetDateTime deadline = deadline(executionPayload(result, request.requestId(), request.executionId()), now);
        requireOwner(result, request.workerInstanceId(), now, true);
        if (!result.heartbeat(request.requestId(), request.executionId(), request.workerInstanceId(),
                now, cappedLease(now, deadline))) fail(409, "STALE_EXECUTION");
        analysisResultWriter.save(result);
        return control(result, request.requestId(), request.executionId(), now);
    }

    @Transactional
    public AnalysisResultIngestionDisposition ingestResult(Long analysisId, AnalysisRunPodResultCommand request) {
        if (!"voice-coaching.runpod-analysis-result.v1".equals(request.schemaVersion())
                || !analysisId.equals(request.analysisId())) fail(422, "VALIDATION_FAILED");
        AnalysisResult result = find(analysisId);
        requireIdentity(result, request.requestId(), request.executionId());
        if (!result.getRecording().getId().equals(request.recordingId())) fail(422, "VALIDATION_FAILED");
        if (result.getWorkerInstanceId() == null) fail(409, "CLAIM_REQUIRED");
        if (!request.workerInstanceId().equals(result.getWorkerInstanceId())) fail(409, "WORKER_CONFLICT");
        if (result.isConflictingResultEvent(request.eventId(), request.payloadSha256())) fail(409, "RESULT_EVENT_CONFLICT");
        // Identical committed events remain acknowledgeable after lease/deadline expiry.
        if (result.isDuplicateResultEvent(request.eventId(), request.payloadSha256())) {
            if (result.getRecording().getDeletedAt() != null || result.getStatus() != request.status()
                    || (result.getFailureCode() != null && result.getFailureCode().contains("cancel"))) fail(409, "ANALYSIS_CANCELLED");
            return AnalysisResultIngestionDisposition.IGNORED_DUPLICATE;
        }
        if (result.getLastResultEventId() != null) fail(409, "RESULT_ALREADY_FINALIZED");
        requireActive(result, request.requestId(), request.executionId());
        OffsetDateTime now = now();
        deadline(executionPayload(result, request.requestId(), request.executionId()), now);
        requireOwner(result, request.workerInstanceId(), now, true);
        var evidence = request.workerResult().pronunciationEvidence();
        if (evidence != null && evidence.selectedEndMs() != null
                && (result.getRecording().getDurationMs() == null
                || evidence.selectedEndMs() > result.getRecording().getDurationMs())) fail(422, "VALIDATION_FAILED");
        try {
            var disposition = ingestionService.ingest(request.workerResult(), request.executionId(), request.payloadSha256());
            if (disposition != AnalysisResultIngestionDisposition.APPLIED) fail(409, "RESULT_ALREADY_FINALIZED");
            return disposition;
        } catch (IllegalArgumentException error) {
            throw new RunPodContractException(422, "VALIDATION_FAILED");
        }
    }

    private AnalysisResult find(Long id) {
        return analysisResultReader.findForIngestion(id).orElseThrow(() -> new RunPodContractException(404, "TARGET_NOT_FOUND"));
    }

    private void requireIdentity(AnalysisResult result, UUID request, UUID execution) {
        if (!result.isForActiveRequest(request) || !result.isForActiveExecution(execution)) fail(409, "STALE_EXECUTION");
    }

    private void requireActive(AnalysisResult result, UUID request, UUID execution) {
        requireIdentity(result, request, execution);
        if (result.getRecording().getDeletedAt() != null || !Boolean.TRUE.equals(result.getRecording().getSelected())
                || (result.getFailureCode() != null && result.getFailureCode().contains("cancel"))) fail(409, "ANALYSIS_CANCELLED");
        if (result.getStatus() == AnalysisStatus.COMPLETED || result.getStatus() == AnalysisStatus.FAILED) fail(409, "ANALYSIS_TERMINAL");
    }

    private String executionPayload(AnalysisResult result, UUID request, UUID execution) {
        var outbox = outboxRepository.findByEventIdAndExecutionIdAndTransport(request.toString(), execution.toString(), "RUNPOD_HTTP")
                .orElseThrow(() -> new RunPodContractException(409, "UNKNOWN_EXECUTION"));
        if (!result.getId().equals(outbox.getAnalysisResult().getId())) fail(409, "UNKNOWN_EXECUTION");
        return outbox.getPayload();
    }

    private OffsetDateTime deadline(String payload, OffsetDateTime now) {
        var json = contract.parse(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), "analysisRequest");
        OffsetDateTime deadline = OffsetDateTime.parse(json.get("deadlineAt").asText());
        if (!deadline.isAfter(now)) fail(409, "DEADLINE_EXCEEDED");
        return deadline;
    }

    private void requireOwner(AnalysisResult result, String worker, OffsetDateTime now, boolean required) {
        if (result.getWorkerInstanceId() == null) {
            if (required) fail(409, "CLAIM_REQUIRED");
            return;
        }
        if (!worker.equals(result.getWorkerInstanceId())) fail(409, "WORKER_CONFLICT");
        if (result.getClaimExpiresAt() == null || !result.getClaimExpiresAt().isAfter(now)) fail(409, "LEASE_EXPIRED");
    }

    private OffsetDateTime cappedLease(OffsetDateTime now, OffsetDateTime deadline) {
        OffsetDateTime expires = now.plus(properties.getClaimTtl());
        if (!expires.isAfter(now)) fail(503, "NOT_READY");
        return expires.isBefore(deadline) ? expires : deadline;
    }

    private AnalysisRunPodControlData control(AnalysisResult result, UUID request, UUID execution, OffsetDateTime now) {
        return new AnalysisRunPodControlData(result.getId(), request, execution, result.getWorkerInstanceId(),
                RunPodContract.timestamp(now), RunPodContract.timestamp(result.getClaimExpiresAt()), true);
    }

    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS); }
    private static void fail(int status, String reason) { throw new RunPodContractException(status, reason); }
}
