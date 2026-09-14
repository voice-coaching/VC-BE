package org.example.voice.analysis.infrastructure.runpod;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisRequestOutbox;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.port.AnalysisJobPublisher;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis", name = "transport", havingValue = "runpod_http")
public class RunPodOutboxAnalysisJobPublisher implements AnalysisJobPublisher {

    private final AnalysisRequestOutboxJpaRepository outboxRepository;
    private final AnalysisResultJpaRepository analysisResultRepository;
    private final RunPodAnalysisPayloadCodec codec;
    private final RunPodAnalysisProperties properties;

    @Override
    @Transactional
    public void publish(AnalysisWorkerRequest request) {
        if (!properties.isConfigured()) {
            throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        }
        AnalysisResult analysisResult = analysisResultRepository.findById(request.analysisId())
                .orElseThrow(() -> new IllegalStateException("analysis result disappeared before outbox write"));
        if (!analysisResult.isForActiveRequest(request.eventId())) {
            throw new IllegalStateException("analysis request event does not match active analysis request");
        }
        UUID executionId = UUID.randomUUID();
        analysisResult.assignExecution(executionId);
        RunPodAnalysisJobRequest runPodRequest = RunPodAnalysisJobRequest.from(
                request,
                executionId,
                analysisResult.getRecording().getId(),
                OffsetDateTime.now(ZoneOffset.UTC).plus(properties.getExecutionTimeout())
        );
        String payload = codec.encodeRequest(runPodRequest);
        if (codec.payloadBytes(payload) > properties.getMaximumPayloadBytes()) {
            throw new IllegalStateException("runpod_analysis_request_payload_size_invalid");
        }
        outboxRepository.save(AnalysisRequestOutbox.pendingHttp(
                request.eventId(),
                executionId,
                analysisResult,
                payload
        ));
    }
}
