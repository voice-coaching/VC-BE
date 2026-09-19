package org.example.voice.analysis.infrastructure.stream;

import org.example.voice.analysis.domain.entity.AnalysisCancellationOutbox;
import org.example.voice.analysis.domain.entity.AnalysisRequestOutbox;
import org.example.voice.analysis.domain.type.AnalysisCancellationOutboxStatus;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;
import org.example.voice.analysis.infrastructure.AnalysisCancellationOutboxJpaRepository;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Observes only aggregate queue state; message payloads and identifiers are never read. */
@Component
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class AnalysisOperationsObserver {

    private final StringRedisTemplate redis;
    private final AnalysisStreamProperties properties;
    private final AnalysisRequestOutboxJpaRepository requestOutbox;
    private final AnalysisCancellationOutboxJpaRepository cancellationOutbox;
    private final AnalysisStreamMetrics metrics;

    public AnalysisOperationsObserver(
            @Qualifier("analysisStreamRedisTemplate") StringRedisTemplate redis,
            AnalysisStreamProperties properties,
            AnalysisRequestOutboxJpaRepository requestOutbox,
            AnalysisCancellationOutboxJpaRepository cancellationOutbox,
            AnalysisStreamMetrics metrics
    ) {
        this.redis = redis;
        this.properties = properties;
        this.requestOutbox = requestOutbox;
        this.cancellationOutbox = cancellationOutbox;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${analysis.stream.observation-poll-interval:PT30S}")
    public void observe() {
        observeTransport();
        observeOutboxes();
    }

    private void observeTransport() {
        try {
            metrics.observeTransport(
                    size(properties.getRequestStream()),
                    pending(properties.getRequestStream(), properties.getRequestConsumerGroup()),
                    size(properties.getRequestDeadLetterStream()),
                    size(properties.getResultStream()),
                    pending(properties.getResultStream(), properties.getResultConsumerGroup()),
                    size(properties.getResultDeadLetterStream())
            );
        } catch (RuntimeException error) {
            metrics.transportObservationFailed();
        }
    }

    private void observeOutboxes() {
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            long requestPending = requestOutbox.countByStatus(AnalysisRequestOutboxStatus.PENDING);
            long cancellationPending = cancellationOutbox.countByStatus(
                    AnalysisCancellationOutboxStatus.PENDING
            );
            metrics.observeOutboxes(
                    requestPending,
                    oldestAgeSeconds(
                            requestOutbox.findFirstByStatusOrderByCreatedAtAsc(
                                    AnalysisRequestOutboxStatus.PENDING
                            ).orElse(null),
                            now
                    ),
                    cancellationPending,
                    oldestAgeSeconds(
                            cancellationOutbox.findFirstByStatusOrderByCreatedAtAsc(
                                    AnalysisCancellationOutboxStatus.PENDING
                            ).orElse(null),
                            now
                    )
            );
        } catch (RuntimeException error) {
            metrics.outboxObservationFailed();
        }
    }

    private long size(String stream) {
        Long size = redis.opsForStream().size(stream);
        if (size == null || size < 0) {
            throw new IllegalStateException("analysis_stream_size_unavailable");
        }
        return size;
    }

    private long pending(String stream, String group) {
        PendingMessagesSummary summary = redis.opsForStream().pending(stream, group);
        if (summary == null || summary.getTotalPendingMessages() < 0) {
            throw new IllegalStateException("analysis_stream_pending_unavailable");
        }
        return summary.getTotalPendingMessages();
    }

    private static long oldestAgeSeconds(AnalysisRequestOutbox value, OffsetDateTime now) {
        return value == null ? 0 : ageSeconds(value.getCreatedAt(), now);
    }

    private static long oldestAgeSeconds(AnalysisCancellationOutbox value, OffsetDateTime now) {
        return value == null ? 0 : ageSeconds(value.getCreatedAt(), now);
    }

    private static long ageSeconds(OffsetDateTime createdAt, OffsetDateTime now) {
        return Math.max(0, ChronoUnit.SECONDS.between(createdAt, now));
    }
}
