package org.example.voice.training.infrastructure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.voice.training.domain.entity.RecordingDeletionOutbox;
import org.example.voice.training.domain.port.RecordingObjectStoragePort;
import org.example.voice.training.domain.type.RecordingDeletionReason;
import org.example.voice.training.domain.type.RecordingDeletionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordingDeletionOutboxDispatcherTest {

    @Test
    void dispatchesEachCandidateThroughTheSingleDeliveryBoundary() {
        RecordingDeletionOutboxJpaRepository repository = mock(RecordingDeletionOutboxJpaRepository.class);
        RecordingDeletionDelivery delivery = mock(RecordingDeletionDelivery.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RecordingDeletionMetrics metrics = new RecordingDeletionMetrics(registry);
        when(repository.findDispatchCandidateIds(
                eq(RecordingDeletionStatus.PENDING), any(), any(Pageable.class)
        )).thenReturn(List.of(11L, 12L));

        new RecordingDeletionOutboxDispatcher(repository, delivery, metrics).dispatch();

        verify(delivery).deliver(11L);
        verify(delivery).deliver(12L);
        verify(delivery).purgeDeletedBefore(any(OffsetDateTime.class));
        assertThat(registry.get("voice.storage.deletion.pending").gauge().value()).isZero();
    }

    @Test
    void continuesAfterOneDeliveryTransactionFails() {
        RecordingDeletionOutboxJpaRepository repository = mock(RecordingDeletionOutboxJpaRepository.class);
        RecordingDeletionDelivery delivery = mock(RecordingDeletionDelivery.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RecordingDeletionMetrics metrics = new RecordingDeletionMetrics(registry);
        when(repository.findDispatchCandidateIds(
                eq(RecordingDeletionStatus.PENDING), any(), any(Pageable.class)
        )).thenReturn(List.of(11L, 12L));
        doThrow(new IllegalStateException("database unavailable")).when(delivery).deliver(11L);

        new RecordingDeletionOutboxDispatcher(repository, delivery, metrics).dispatch();

        verify(delivery).deliver(12L);
        assertThat(registry.get("voice.storage.deletion.delivery.transaction.failures")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void exposesDeletionBacklogWithoutHighCardinalityLabels() {
        RecordingDeletionOutbox oldest = pending();
        ReflectionTestUtils.setField(
                oldest,
                "createdAt",
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(75)
        );
        RecordingDeletionOutboxJpaRepository repository = mock(RecordingDeletionOutboxJpaRepository.class);
        RecordingDeletionDelivery delivery = mock(RecordingDeletionDelivery.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RecordingDeletionMetrics metrics = new RecordingDeletionMetrics(registry);
        when(repository.findDispatchCandidateIds(any(), any(), any())).thenReturn(List.of());
        when(repository.countByStatus(RecordingDeletionStatus.PENDING)).thenReturn(3L);
        when(repository.countByStatus(RecordingDeletionStatus.FAILED)).thenReturn(2L);
        when(repository.findFirstByStatusOrderByCreatedAtAsc(RecordingDeletionStatus.PENDING))
                .thenReturn(Optional.of(oldest));

        new RecordingDeletionOutboxDispatcher(repository, delivery, metrics).dispatch();

        assertThat(registry.get("voice.storage.deletion.pending").gauge().value()).isEqualTo(3);
        assertThat(registry.get("voice.storage.deletion.failed").gauge().value()).isEqualTo(2);
        assertThat(registry.get("voice.storage.deletion.oldest.pending.age.seconds")
                .gauge().value()).isGreaterThanOrEqualTo(74);
    }

    @Test
    void deliversOneObjectAndMarksItDeleted() {
        RecordingDeletionOutbox deletion = pending();
        ReflectionTestUtils.setField(deletion, "id", 11L);
        RecordingDeletionOutboxJpaRepository repository = mock(RecordingDeletionOutboxJpaRepository.class);
        RecordingObjectStoragePort storage = mock(RecordingObjectStoragePort.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RecordingDeletionMetrics metrics = new RecordingDeletionMetrics(registry);
        when(repository.findForDeliveryById(11L)).thenReturn(Optional.of(deletion));

        new RecordingDeletionDelivery(repository, storage, metrics).deliver(11L);

        verify(storage).deleteObject(9L, 7L, deletion.getObjectKey());
        assertThat(deletion.getStatus()).isEqualTo(RecordingDeletionStatus.DELETED);
        assertThat(deletion.getDeletedAt()).isNotNull();
        assertThat(registry.get("voice.storage.deletion.completed").counter().count()).isEqualTo(1);
    }

    @Test
    void deliversOneObjectAndRecordsRetryState() {
        RecordingDeletionOutbox deletion = pending();
        ReflectionTestUtils.setField(deletion, "id", 11L);
        RecordingDeletionOutboxJpaRepository repository = mock(RecordingDeletionOutboxJpaRepository.class);
        RecordingObjectStoragePort storage = mock(RecordingObjectStoragePort.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RecordingDeletionMetrics metrics = new RecordingDeletionMetrics(registry);
        when(repository.findForDeliveryById(11L)).thenReturn(Optional.of(deletion));
        doThrow(new IllegalStateException("unavailable"))
                .when(storage).deleteObject(9L, 7L, deletion.getObjectKey());

        new RecordingDeletionDelivery(repository, storage, metrics).deliver(11L);

        assertThat(deletion.getStatus()).isEqualTo(RecordingDeletionStatus.PENDING);
        assertThat(deletion.getAttemptCount()).isEqualTo(1);
        assertThat(deletion.getLastErrorCode()).isEqualTo("object_storage_delete_failed");
        assertThat(registry.get("voice.storage.deletion.retries").counter().count()).isEqualTo(1);
    }

    private static RecordingDeletionOutbox pending() {
        return RecordingDeletionOutbox.pending(
                9L,
                7L,
                "recordings/users/9/sessions/7/normalized/4adfe173-0691-4e89-b94e-a5c5c5085826.wav",
                RecordingDeletionReason.HISTORY_DELETED
        );
    }
}
