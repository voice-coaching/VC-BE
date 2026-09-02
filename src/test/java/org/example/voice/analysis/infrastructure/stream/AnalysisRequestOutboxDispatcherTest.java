package org.example.voice.analysis.infrastructure.stream;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.voice.analysis.domain.entity.AnalysisRequestOutbox;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisRequestOutboxDispatcherTest {

    @Test
    void commitsEachPublishedOutboxRecordInItsOwnTransaction() {
        AnalysisRequestOutboxJpaRepository outbox = mock(AnalysisRequestOutboxJpaRepository.class);
        AnalysisRequestOutbox event = mock(AnalysisRequestOutbox.class);
        when(event.getPayload()).thenReturn("synthetic-payload");
        when(outbox.findFirstByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                eq(AnalysisRequestOutboxStatus.PENDING), any(OffsetDateTime.class)
        )).thenReturn(Optional.of(event), Optional.empty());
        RedisAnalysisRequestPublisher publisher = mock(RedisAnalysisRequestPublisher.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(ignored -> new SimpleTransactionStatus());

        new AnalysisRequestOutboxDispatcher(
                outbox,
                mock(AnalysisResultJpaRepository.class),
                publisher,
                new AnalysisStreamProperties(),
                new AnalysisStreamMetrics(new SimpleMeterRegistry()),
                transactionManager
        ).dispatchPending();

        verify(publisher).publish("synthetic-payload");
        verify(event).markPublished();
        verify(transactionManager, times(2)).commit(any());
    }
}
