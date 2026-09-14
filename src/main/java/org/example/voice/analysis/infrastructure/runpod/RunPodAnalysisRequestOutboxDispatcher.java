package org.example.voice.analysis.infrastructure.runpod;

import lombok.extern.slf4j.Slf4j;
import org.example.voice.analysis.domain.entity.AnalysisRequestOutbox;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "analysis", name = "transport", havingValue = "runpod_http")
public class RunPodAnalysisRequestOutboxDispatcher {

    private static final String DELIVERY_FAILURE_CODE = "runpod_analysis_request_delivery_failed";
    private static final String DELIVERY_FAILURE_REASON = "분석 작업을 RunPod에 전달하지 못했습니다. 다시 시도해 주세요.";

    private final AnalysisRequestOutboxJpaRepository outboxRepository;
    private final AnalysisResultJpaRepository analysisResultRepository;
    private final RunPodAnalysisClient client;
    private final RunPodAnalysisPayloadCodec codec;
    private final RunPodAnalysisProperties properties;
    private final TransactionTemplate transactionTemplate;

    public RunPodAnalysisRequestOutboxDispatcher(
            AnalysisRequestOutboxJpaRepository outboxRepository,
            AnalysisResultJpaRepository analysisResultRepository,
            RunPodAnalysisClient client,
            RunPodAnalysisPayloadCodec codec,
            RunPodAnalysisProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.outboxRepository = outboxRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.client = client;
        this.codec = codec;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${analysis.runpod.outbox-poll-interval:PT1S}")
    public void dispatchPending() {
        for (int dispatched = 0; dispatched < properties.getBatchSize(); dispatched++) {
            Boolean found = transactionTemplate.execute(status -> dispatchNext());
            if (!Boolean.TRUE.equals(found)) {
                return;
            }
        }
    }

    private boolean dispatchNext() {
        return outboxRepository.findFirstByTransportAndStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                        "RUNPOD_HTTP",
                        AnalysisRequestOutboxStatus.PENDING,
                        OffsetDateTime.now(ZoneOffset.UTC)
                )
                .map(event -> {
                    dispatch(event);
                    return true;
                })
                .orElse(false);
    }

    private void dispatch(AnalysisRequestOutbox event) {
        try {
            RunPodAnalysisJobRequest request = codec.decodeRequest(event.getPayload());
            RunPodAnalysisJobAccepted accepted = client.submit(request);
            validateAccepted(request, accepted);
            event.markDelivered(accepted == null ? null : accepted.executionId().toString());
        } catch (RunPodAnalysisDeliveryException error) {
            log.warn("runpod analysis request delivery failed: eventId={}, code={}", event.getEventId(), error.code());
            int maxAttempts = error.isRetryable() ? properties.getDispatchMaxAttempts() : 1;
            if (event.recordDispatchFailure(error.code(), maxAttempts)) {
                failAnalysisIfCurrent(event);
            }
        } catch (RuntimeException error) {
            log.warn("runpod analysis request delivery failed: eventId={}", event.getEventId());
            if (event.recordDispatchFailure(DELIVERY_FAILURE_CODE, properties.getDispatchMaxAttempts())) {
                failAnalysisIfCurrent(event);
            }
        }
    }

    private void validateAccepted(RunPodAnalysisJobRequest request, RunPodAnalysisJobAccepted accepted) {
        if (accepted == null
                || !request.requestId().equals(accepted.requestId())
                || !request.executionId().equals(accepted.executionId())) {
            throw new RunPodAnalysisDeliveryException("runpod_acceptance_contract_invalid", false, null);
        }
    }

    private void failAnalysisIfCurrent(AnalysisRequestOutbox event) {
        analysisResultRepository.findById(event.getAnalysisResult().getId())
                .filter(result -> result.isForActiveRequest(UUID.fromString(event.getEventId())))
                .ifPresent(result -> result.fail(
                        DELIVERY_FAILURE_CODE,
                        DELIVERY_FAILURE_REASON,
                        null,
                        null
                ));
    }
}
