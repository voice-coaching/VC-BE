package org.example.voice.analysis.infrastructure.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice.analysis.domain.entity.AnalysisRequestOutbox;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Publishes durable requests at least once and converts exhausted delivery into a stable failure. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class AnalysisRequestOutboxDispatcher {

    private static final String DELIVERY_FAILURE_CODE = "analysis_request_delivery_failed";
    private static final String DELIVERY_FAILURE_REASON = "분석 작업을 전달하지 못했습니다. 다시 시도해 주세요.";

    private final AnalysisRequestOutboxJpaRepository outboxRepository;
    private final AnalysisResultJpaRepository analysisResultRepository;
    private final RedisAnalysisRequestPublisher redisPublisher;
    private final AnalysisStreamProperties properties;

    @Scheduled(fixedDelayString = "${analysis.stream.outbox-poll-interval:PT1S}")
    @Transactional
    public void dispatchPending() {
        List<AnalysisRequestOutbox> pending = outboxRepository.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                AnalysisRequestOutboxStatus.PENDING,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        int limit = Math.min(properties.getBatchSize(), pending.size());
        for (AnalysisRequestOutbox event : pending.subList(0, limit)) {
            dispatch(event);
        }
    }

    private void dispatch(AnalysisRequestOutbox event) {
        try {
            redisPublisher.publish(event.getPayload());
            event.markPublished();
        } catch (RuntimeException error) {
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
