package org.example.voice.analysis.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.AnalysisRunPodClaimCommand;
import org.example.voice.analysis.domain.model.AnalysisRunPodControlData;
import org.example.voice.analysis.domain.model.AnalysisRunPodHeartbeatCommand;
import org.example.voice.analysis.domain.model.AnalysisRunPodResultCommand;
import org.example.voice.analysis.domain.port.AnalysisResultReader;
import org.example.voice.analysis.domain.port.AnalysisResultWriter;
import org.example.voice.analysis.domain.type.AnalysisResultIngestionDisposition;
import org.example.voice.analysis.infrastructure.runpod.RunPodAnalysisProperties;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalysisRunPodCallbackService {

    private static final String CLAIM_SCHEMA_VERSION = "voice-coaching.runpod-analysis-claim.v1";
    private static final String HEARTBEAT_SCHEMA_VERSION = "voice-coaching.runpod-analysis-heartbeat.v1";
    private static final String RESULT_SCHEMA_VERSION = "voice-coaching.runpod-analysis-result.v1";

    private final AnalysisResultReader analysisResultReader;
    private final AnalysisResultWriter analysisResultWriter;
    private final AnalysisResultIngestionService ingestionService;
    private final RunPodAnalysisProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Transactional
    public AnalysisRunPodControlData claim(Long analysisId, AnalysisRunPodClaimCommand request) {
        requireSchema(CLAIM_SCHEMA_VERSION, request.schemaVersion());
        AnalysisResult result = find(analysisId);
        boolean accepted = result.claim(
                request.requestId(),
                request.executionId(),
                request.workerInstanceId(),
                request.claimedUntil()
        );
        if (!accepted) {
            throw new BaseException(ErrorCode.ANALYSIS_INTERNAL_STALE_EXECUTION);
        }
        analysisResultWriter.save(result);
        return new AnalysisRunPodControlData(analysisId, request.requestId(), request.executionId(),
                true, false, true);
    }

    @Transactional
    public AnalysisRunPodControlData heartbeat(Long analysisId, AnalysisRunPodHeartbeatCommand request) {
        requireSchema(HEARTBEAT_SCHEMA_VERSION, request.schemaVersion());
        AnalysisResult result = find(analysisId);
        OffsetDateTime heartbeatAt = request.heartbeatAt() == null
                ? OffsetDateTime.now(ZoneOffset.UTC)
                : request.heartbeatAt();
        boolean accepted = result.heartbeat(
                request.requestId(),
                request.executionId(),
                request.workerInstanceId(),
                heartbeatAt,
                heartbeatAt.plus(properties.getClaimTtl())
        );
        if (!accepted) {
            throw new BaseException(ErrorCode.ANALYSIS_INTERNAL_STALE_EXECUTION);
        }
        analysisResultWriter.save(result);
        return new AnalysisRunPodControlData(analysisId, request.requestId(), request.executionId(),
                true, false, true);
    }

    @Transactional
    public AnalysisResultIngestionDisposition ingestResult(
            Long analysisId,
            AnalysisRunPodResultCommand request
    ) {
        requireSchema(RESULT_SCHEMA_VERSION, request.schemaVersion());
        if (!analysisId.equals(request.analysisId())) {
            throw new BaseException(ErrorCode.ANALYSIS_INTERNAL_CONTRACT_INVALID);
        }
        AnalysisResult result = find(analysisId);
        if (!result.getRecording().getId().equals(request.recordingId())) {
            throw new BaseException(ErrorCode.ANALYSIS_INTERNAL_CONTRACT_INVALID);
        }
        if (result.isConflictingResultEvent(request.eventId(), payloadSha256(request))) {
            throw new BaseException(ErrorCode.ANALYSIS_INTERNAL_RESULT_CONFLICT);
        }
        try {
            AnalysisResultIngestionDisposition disposition = ingestionService.ingest(
                    request.workerResult(),
                    request.executionId(),
                    payloadSha256(request)
            );
            if (disposition == AnalysisResultIngestionDisposition.IGNORED_STALE) {
                throw new BaseException(ErrorCode.ANALYSIS_INTERNAL_STALE_EXECUTION);
            }
            return disposition;
        } catch (IllegalArgumentException error) {
            throw new BaseException(ErrorCode.ANALYSIS_INTERNAL_CONTRACT_INVALID);
        }
    }

    private AnalysisResult find(Long analysisId) {
        return analysisResultReader.findForIngestion(analysisId)
                .orElseThrow(() -> new BaseException(ErrorCode.ANALYSIS_NOT_FOUND));
    }

    private static void requireSchema(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new BaseException(ErrorCode.ANALYSIS_INTERNAL_CONTRACT_INVALID);
        }
    }

    private String payloadSha256(AnalysisRunPodResultCommand request) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (JsonProcessingException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("callback payload digest failed", error);
        }
    }
}
