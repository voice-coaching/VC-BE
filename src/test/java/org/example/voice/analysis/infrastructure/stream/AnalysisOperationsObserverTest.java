package org.example.voice.analysis.infrastructure.stream;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.voice.analysis.domain.entity.AnalysisCancellationOutbox;
import org.example.voice.analysis.domain.entity.AnalysisRequestOutbox;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.type.AnalysisCancellationOutboxStatus;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;
import org.example.voice.analysis.infrastructure.AnalysisCancellationOutboxJpaRepository;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisOperationsObserverTest {

    @Test
    void exposesTransportAndOutboxBacklogsWithoutIdentifiers() {
        AnalysisStreamProperties properties = new AnalysisStreamProperties();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(streams.size(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "analysis:request:v1" -> 7L;
            case "analysis:request:dlq:v1" -> 2L;
            case "analysis:result:v1" -> 5L;
            case "analysis:result:dlq:v1" -> 1L;
            default -> throw new IllegalArgumentException("unexpected stream");
        });
        PendingMessagesSummary requestPending = mock(PendingMessagesSummary.class);
        PendingMessagesSummary resultPending = mock(PendingMessagesSummary.class);
        when(requestPending.getTotalPendingMessages()).thenReturn(3L);
        when(resultPending.getTotalPendingMessages()).thenReturn(4L);
        when(streams.pending(properties.getRequestStream(), properties.getRequestConsumerGroup()))
                .thenReturn(requestPending);
        when(streams.pending(properties.getResultStream(), properties.getResultConsumerGroup()))
                .thenReturn(resultPending);

        AnalysisRequestOutbox request = AnalysisRequestOutbox.pending(
                UUID.randomUUID(), mock(AnalysisResult.class), "{}"
        );
        AnalysisCancellationOutbox cancellation = AnalysisCancellationOutbox.pending(UUID.randomUUID());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ReflectionTestUtils.setField(request, "createdAt", now.minusSeconds(91));
        ReflectionTestUtils.setField(cancellation, "createdAt", now.minusSeconds(31));
        AnalysisRequestOutboxJpaRepository requests = mock(AnalysisRequestOutboxJpaRepository.class);
        AnalysisCancellationOutboxJpaRepository cancellations = mock(
                AnalysisCancellationOutboxJpaRepository.class
        );
        when(requests.countByStatus(AnalysisRequestOutboxStatus.PENDING)).thenReturn(6L);
        when(requests.findFirstByStatusOrderByCreatedAtAsc(AnalysisRequestOutboxStatus.PENDING))
                .thenReturn(Optional.of(request));
        when(cancellations.countByStatus(AnalysisCancellationOutboxStatus.PENDING)).thenReturn(2L);
        when(cancellations.findFirstByStatusOrderByCreatedAtAsc(
                AnalysisCancellationOutboxStatus.PENDING
        )).thenReturn(Optional.of(cancellation));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AnalysisStreamMetrics metrics = new AnalysisStreamMetrics(registry);

        new AnalysisOperationsObserver(redis, properties, requests, cancellations, metrics).observe();

        assertGauge(registry, "voice.analysis.stream.request.outstanding", 7);
        assertGauge(registry, "voice.analysis.stream.request.pel.pending", 3);
        assertGauge(registry, "voice.analysis.stream.request.dead.letter.entries", 2);
        assertGauge(registry, "voice.analysis.stream.result.outstanding", 5);
        assertGauge(registry, "voice.analysis.stream.result.pel.pending", 4);
        assertGauge(registry, "voice.analysis.stream.result.dead.letter.entries", 1);
        assertGauge(registry, "voice.analysis.outbox.request.pending", 6);
        assertThat(registry.get("voice.analysis.outbox.request.oldest.pending.age.seconds")
                .gauge().value()).isGreaterThanOrEqualTo(90);
        assertGauge(registry, "voice.analysis.outbox.cancellation.pending", 2);
        assertThat(registry.get("voice.analysis.outbox.cancellation.oldest.pending.age.seconds")
                .gauge().value()).isGreaterThanOrEqualTo(30);
    }

    @Test
    void marksOnlyTheFailedObservationSourceUnknown() {
        AnalysisStreamProperties properties = new AnalysisStreamProperties();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForStream()).thenThrow(new IllegalStateException("redis unavailable"));
        AnalysisRequestOutboxJpaRepository requests = mock(AnalysisRequestOutboxJpaRepository.class);
        AnalysisCancellationOutboxJpaRepository cancellations = mock(
                AnalysisCancellationOutboxJpaRepository.class
        );
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AnalysisStreamMetrics metrics = new AnalysisStreamMetrics(registry);

        new AnalysisOperationsObserver(redis, properties, requests, cancellations, metrics).observe();

        assertGauge(registry, "voice.analysis.stream.request.outstanding", -1);
        assertGauge(registry, "voice.analysis.outbox.request.pending", 0);
        assertThat(registry.get("voice.analysis.stream.observation.failures").counter().count())
                .isEqualTo(1);
    }

    private static void assertGauge(SimpleMeterRegistry registry, String name, double expected) {
        assertThat(registry.get(name).gauge().value()).isEqualTo(expected);
    }
}
