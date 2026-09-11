package org.example.voice.analysis.infrastructure.runpod;

import lombok.extern.slf4j.Slf4j;
import org.example.voice.analysis.domain.entity.AnalysisCancellationOutbox;
import org.example.voice.analysis.domain.type.AnalysisCancellationOutboxStatus;
import org.example.voice.analysis.infrastructure.AnalysisCancellationOutboxJpaRepository;
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
public class RunPodAnalysisCancellationOutboxDispatcher {

    private static final String DELIVERY_FAILURE_CODE = "runpod_analysis_cancellation_delivery_failed";

    private final AnalysisCancellationOutboxJpaRepository cancellationRepository;
    private final AnalysisResultJpaRepository analysisResultRepository;
    private final RunPodAnalysisClient client;
    private final RunPodAnalysisProperties properties;
    private final TransactionTemplate transactionTemplate;

    public RunPodAnalysisCancellationOutboxDispatcher(
            AnalysisCancellationOutboxJpaRepository cancellationRepository,
            AnalysisResultJpaRepository analysisResultRepository,
            RunPodAnalysisClient client,
            RunPodAnalysisProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.cancellationRepository = cancellationRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.client = client;
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
        return cancellationRepository.findFirstByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                        AnalysisCancellationOutboxStatus.PENDING,
                        OffsetDateTime.now(ZoneOffset.UTC)
                )
                .map(event -> {
                    dispatch(event);
                    return true;
                })
                .orElse(false);
    }

    private void dispatch(AnalysisCancellationOutbox event) {
        UUID requestId = UUID.fromString(event.getRequestEventId());
        analysisResultRepository.findFirstByActiveRequestEventId(event.getRequestEventId())
                .filter(result -> result.getActiveExecutionId() != null)
                .ifPresentOrElse(result -> cancel(event, requestId, UUID.fromString(result.getActiveExecutionId())),
                        event::markPublished);
    }

    private void cancel(AnalysisCancellationOutbox event, UUID requestId, UUID executionId) {
        try {
            client.cancel(requestId, executionId);
            event.markPublished();
        } catch (RunPodAnalysisDeliveryException error) {
            if (error.isRetryable()) {
                event.recordDeliveryFailure(error.code());
            } else {
                event.markPublished();
            }
            log.warn("runpod analysis cancellation delivery failed: outboxId={}, code={}", event.getId(), error.code());
        } catch (RuntimeException error) {
            event.recordDeliveryFailure(DELIVERY_FAILURE_CODE);
            log.warn("runpod analysis cancellation delivery failed: outboxId={}", event.getId());
        }
    }
}
