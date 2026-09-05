package org.example.voice.analysis.infrastructure.stream;

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

/** Publishes durable requests at least once and converts exhausted delivery into a stable failure. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class AnalysisRequestOutboxDispatcher {

    private static final String DELIVERY_FAILURE_CODE = "analysis_request_delivery_failed";
    private static final String DELIVERY_FAILURE_REASON = "분석 작업을 전달하지 못했습니다. 다시 시도해 주세요.";

    private final AnalysisRequestOutboxJpaRepository outboxRepository;
    private final AnalysisResultJpaRepository analysisResultRepository;
    private final RedisAnalysisRequestPublisher redisPublisher;
    private final AnalysisStreamProperties properties;
    private final AnalysisStreamMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    public AnalysisRequestOutboxDispatcher(
            AnalysisRequestOutboxJpaRepository outboxRepository,
            AnalysisResultJpaRepository analysisResultRepository,
            RedisAnalysisRequestPublisher redisPublisher,
            AnalysisStreamProperties properties,
            AnalysisStreamMetrics metrics,
            PlatformTransactionManager transactionManager
    ) {
        this.outboxRepository = outboxRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.redisPublisher = redisPublisher;
        this.properties = properties;
        this.metrics = metrics;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${analysis.stream.outbox-poll-interval:PT1S}")
    public void dispatchPending() {
        for (int dispatched = 0; dispatched < properties.getBatchSize(); dispatched++) {
            Boolean found = transactionTemplate.execute(status -> dispatchNext());
            if (!Boolean.TRUE.equals(found)) {
                return;
            }
        }
    }

    private boolean dispatchNext() {
        return outboxRepository.findFirstByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
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
            String streamId = redisPublisher.publish(
                    UUID.fromString(event.getEventId()),
                    event.getPayload()
            );
            event.markPublished(streamId);
        } catch (RuntimeException error) {
            metrics.requestPublishFailed();
            log.warn("analysis request stream publish failed: eventId={}", event.getEventId());
            if (event.recordDispatchFailure(DELIVERY_FAILURE_CODE, properties.getMaxRetries())) {
                failAnalysisIfCurrent(event);
            }
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
