package org.example.voice.analysis.infrastructure.stream;

import lombok.extern.slf4j.Slf4j;
import org.example.voice.analysis.domain.entity.AnalysisCancellationOutbox;
import org.example.voice.analysis.domain.type.AnalysisCancellationOutboxStatus;
import org.example.voice.analysis.infrastructure.AnalysisCancellationOutboxJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Retries safety-critical cancellation tombstones until Redis confirms a write. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class AnalysisCancellationOutboxDispatcher {

    private static final String DELIVERY_FAILURE_CODE = "analysis_cancellation_delivery_failed";

    private final AnalysisCancellationOutboxJpaRepository repository;
    private final RedisAnalysisCancellationPublisher publisher;
    private final AnalysisStreamProperties properties;
    private final AnalysisStreamMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    public AnalysisCancellationOutboxDispatcher(
            AnalysisCancellationOutboxJpaRepository repository,
            RedisAnalysisCancellationPublisher publisher,
            AnalysisStreamProperties properties,
            AnalysisStreamMetrics metrics,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.properties = properties;
        this.metrics = metrics;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${analysis.stream.cancellation-outbox-poll-interval:PT1S}")
    public void dispatchPending() {
        for (int dispatched = 0; dispatched < properties.getBatchSize(); dispatched++) {
            Boolean found = transactionTemplate.execute(status -> dispatchNext());
            if (!Boolean.TRUE.equals(found)) {
                return;
            }
        }
    }

    private boolean dispatchNext() {
        return repository.findFirstByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
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
        try {
            publisher.publish(UUID.fromString(event.getRequestEventId()));
            event.markPublished();
        } catch (RuntimeException error) {
            metrics.cancellationPublishFailed();
            event.recordDeliveryFailure(DELIVERY_FAILURE_CODE);
            log.warn("analysis cancellation tombstone publish failed: outboxId={}", event.getId());
        }
    }
}
