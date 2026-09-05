package org.example.voice.analysis.infrastructure.stream;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.voice.analysis.domain.entity.AnalysisCancellationOutbox;
import org.example.voice.analysis.domain.entity.AnalysisRequestOutbox;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.infrastructure.AnalysisCancellationOutboxJpaRepository;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisStreamRetentionSweeperTest {

    @Test
    void deletesMarkersAndOutboxesOnlyAfterTerminalDbAndMissingStreamEntry() throws Exception {
        Fixture fixture = fixture(false);

        fixture.sweeper.sweep();

        verify(fixture.redis).deleteMarkers(fixture.eventId);
        verify(fixture.cancellations).delete(fixture.cancellation);
        verify(fixture.requests).delete(fixture.request);
        assertThat(fixture.registry.get("voice.analysis.retention.cleaned").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void retainsEveryMarkerWhileTheRequestStreamEntryStillExists() throws Exception {
        Fixture fixture = fixture(true);

        fixture.sweeper.sweep();

        verify(fixture.redis, never()).deleteMarkers(any());
        verify(fixture.cancellations, never()).delete(any());
        verify(fixture.requests, never()).delete(any());
    }

    private static Fixture fixture(boolean streamEntryExists) throws Exception {
        UUID eventId = UUID.randomUUID();
        AnalysisResult result = AnalysisResult.pending(mock(VoiceRecording.class), eventId);
        result.cancel("analysis_session_canceled", "canceled");
        AnalysisRequestOutbox request = AnalysisRequestOutbox.pending(
                eventId, result, "synthetic-payload"
        );
        request.markPublished("123-0");
        AnalysisCancellationOutbox cancellation = AnalysisCancellationOutbox.pending(eventId);
        cancellation.markPublished();
        AnalysisRequestOutboxJpaRepository requests = mock(AnalysisRequestOutboxJpaRepository.class);
        when(requests.findRetentionCandidateIds(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(1L));
        when(requests.findForRetentionById(1L)).thenReturn(Optional.of(request));
        AnalysisCancellationOutboxJpaRepository cancellations = mock(
                AnalysisCancellationOutboxJpaRepository.class
        );
        when(cancellations.findByRequestEventId(eventId.toString()))
                .thenReturn(Optional.of(cancellation));
        RedisAnalysisRetentionStore redis = mock(RedisAnalysisRetentionStore.class);
        when(redis.indexedStreamId(eventId)).thenReturn(Optional.of("123-0"));
        when(redis.requestEntryExists("123-0")).thenReturn(streamEntryExists);
        AnalysisStreamProperties properties = new AnalysisStreamProperties();
        properties.setRetentionAge(Duration.ofMillis(1));
        properties.setRetentionBatchSize(10);
        Thread.sleep(5);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AnalysisStreamRetentionSweeper sweeper = new AnalysisStreamRetentionSweeper(
                requests,
                cancellations,
                redis,
                properties,
                new AnalysisStreamMetrics(registry),
                transactions()
        );
        return new Fixture(
                eventId, request, cancellation, requests, cancellations, redis, registry, sweeper
        );
    }

    private static PlatformTransactionManager transactions() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(ignored -> new SimpleTransactionStatus());
        return manager;
    }

    private record Fixture(
            UUID eventId,
            AnalysisRequestOutbox request,
            AnalysisCancellationOutbox cancellation,
            AnalysisRequestOutboxJpaRepository requests,
            AnalysisCancellationOutboxJpaRepository cancellations,
            RedisAnalysisRetentionStore redis,
            SimpleMeterRegistry registry,
            AnalysisStreamRetentionSweeper sweeper
    ) {
    }
}
