package org.example.voice.analysis.infrastructure.stream;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.voice.analysis.domain.entity.AnalysisCancellationOutbox;
import org.example.voice.analysis.domain.type.AnalysisCancellationOutboxStatus;
import org.example.voice.analysis.infrastructure.AnalysisCancellationOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisCancellationOutboxDispatcherTest {

    @Test
    void publishesOneDurableTombstonePerTransaction() {
        UUID requestEventId = UUID.randomUUID();
        AnalysisCancellationOutbox event = AnalysisCancellationOutbox.pending(requestEventId);
        AnalysisCancellationOutboxJpaRepository repository = repositoryReturning(event);
        RedisAnalysisCancellationPublisher publisher = mock(RedisAnalysisCancellationPublisher.class);
        PlatformTransactionManager transactions = transactions();

        new AnalysisCancellationOutboxDispatcher(
                repository,
                publisher,
                new AnalysisStreamProperties(),
                new AnalysisStreamMetrics(new SimpleMeterRegistry()),
                transactions
        ).dispatchPending();

        verify(publisher).publish(requestEventId);
        assertThat(event.getStatus()).isEqualTo(AnalysisCancellationOutboxStatus.PUBLISHED);
        verify(transactions, times(2)).commit(any());
    }

    @Test
    void redisFailureKeepsCancellationPendingForRetry() {
        UUID requestEventId = UUID.randomUUID();
        AnalysisCancellationOutbox event = AnalysisCancellationOutbox.pending(requestEventId);
        AnalysisCancellationOutboxJpaRepository repository = repositoryReturning(event);
        RedisAnalysisCancellationPublisher publisher = mock(RedisAnalysisCancellationPublisher.class);
        doThrow(new IllegalStateException("synthetic")).when(publisher).publish(requestEventId);

        new AnalysisCancellationOutboxDispatcher(
                repository,
                publisher,
                new AnalysisStreamProperties(),
                new AnalysisStreamMetrics(new SimpleMeterRegistry()),
                transactions()
        ).dispatchPending();

        assertThat(event.getStatus()).isEqualTo(AnalysisCancellationOutboxStatus.PENDING);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastErrorCode()).isEqualTo("analysis_cancellation_delivery_failed");
    }

    private static AnalysisCancellationOutboxJpaRepository repositoryReturning(
            AnalysisCancellationOutbox event
    ) {
        AnalysisCancellationOutboxJpaRepository repository = mock(
                AnalysisCancellationOutboxJpaRepository.class
        );
        when(repository.findFirstByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                eq(AnalysisCancellationOutboxStatus.PENDING), any(OffsetDateTime.class)
        )).thenReturn(Optional.of(event), Optional.empty());
        return repository;
    }

    private static PlatformTransactionManager transactions() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(ignored -> new SimpleTransactionStatus());
        return manager;
    }
}
