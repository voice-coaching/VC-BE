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
    private final org.example.voice.analysis.domain.port.CanonicalExecutionRegistry canonicalExecutions;

    @Transactional
    public AnalysisRunPodControlData claim(Long analysisId, AnalysisRunPodClaimCommand request) {
        AnalysisResult result=find(analysisId);
        OffsetDateTime now=now();
        result.claim(request.requestId(),request.executionId(),request.workerInstanceId(),now.plus(properties.getClaimTtl()));
        analysisResultWriter.save(result);
        return control(result,request.requestId(),request.executionId(),now);
    }

    @Transactional
    public AnalysisRunPodControlData heartbeat(Long analysisId, AnalysisRunPodHeartbeatCommand request) {
        AnalysisResult result=find(analysisId);
        OffsetDateTime now=now();
        result.heartbeat(request.requestId(),request.executionId(),request.workerInstanceId(),now,now.plus(properties.getClaimTtl()));
        analysisResultWriter.save(result);
        return control(result,request.requestId(),request.executionId(),now);
    }

    @Transactional
    public AnalysisResultIngestionDisposition ingestResult(Long analysisId, AnalysisRunPodResultCommand request) {
        boolean scored = "voice-coaching.runpod-analysis-result.v2".equals(request.schemaVersion());
        boolean coached = "voice-coaching.runpod-analysis-result.v3".equals(request.schemaVersion());

        try {
            if (scored && request.status() == AnalysisStatus.COMPLETED) {

            } else {}
        } catch (IllegalArgumentException error) { throw new RunPodContractException(422, "VALIDATION_FAILED"); }
        AnalysisResult result = find(analysisId);
        if (request.coaching() != null) {
            try {  }
            catch (IllegalArgumentException error) { throw new RunPodContractException(422, "VALIDATION_FAILED"); }
        }

        // Identical committed events remain acknowledgeable after lease/deadline expiry.
        if (result.isDuplicateResultEvent(request.eventId(), request.payloadSha256())) {

            return AnalysisResultIngestionDisposition.IGNORED_DUPLICATE;
        }

        OffsetDateTime now = now();
        deadline(executionPayload(result, request.requestId(), request.executionId()), now, request.schemaVersion());

        var evidence = request.workerResult().pronunciationEvidence();

        try {
            var disposition = ingestionService.ingest(request.workerResult(), request.executionId(), request.payloadSha256());

            if (scored && request.status() == AnalysisStatus.COMPLETED) {
                result.applyClovaScore(request.overallScore(), request.scoringEvidence());
                analysisResultWriter.save(result);
            }
            if (coached && request.status() == AnalysisStatus.COMPLETED) {
                result.applyCoaching(request.coaching());
                analysisResultWriter.save(result);
            }
            return disposition;
        } catch (IllegalArgumentException error) {
            throw new RunPodContractException(422, "VALIDATION_FAILED");
        }
    }

    private AnalysisResult find(Long id) {
        var result=analysisResultReader.findForIngestion(id).orElseThrow(() -> new RunPodContractException(404, "TARGET_NOT_FOUND"));
        if(result.isIndependentExecution()) throw new RunPodContractException(409,"EXECUTION_INACTIVE");
        return result;
    }

    private String executionPayload(AnalysisResult result, UUID request, UUID execution) {
        var outbox = outboxRepository.findByEventIdAndExecutionIdAndTransport(request.toString(), execution.toString(), "RUNPOD_HTTP")
                .orElseThrow(() -> new RunPodContractException(409, "UNKNOWN_EXECUTION"));

        return outbox.getPayload();
    }

    private OffsetDateTime deadline(String payload, OffsetDateTime now) {
        return deadline(payload, now, null);
    }

    private OffsetDateTime deadline(String payload, OffsetDateTime now, String resultSchemaVersion) {
        // Select the schema from the immutable outbox request, including v2 for claim/heartbeat.
        var json = contract.parse(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), "analysisRequest");

        OffsetDateTime deadline = OffsetDateTime.parse(json.get("deadlineAt").asText());

        return deadline;
    }

    private OffsetDateTime cappedLease(OffsetDateTime now, OffsetDateTime deadline) {
        OffsetDateTime expires = now.plus(properties.getClaimTtl());

        return expires.isBefore(deadline) ? expires : deadline;
    }

    private AnalysisRunPodControlData control(AnalysisResult result, UUID request, UUID execution, OffsetDateTime now) {
        return new AnalysisRunPodControlData(result.getId(), request, execution, result.getWorkerInstanceId(),
                RunPodContract.timestamp(now), RunPodContract.timestamp(result.getClaimExpiresAt()), true);
    }

    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS); }

}
