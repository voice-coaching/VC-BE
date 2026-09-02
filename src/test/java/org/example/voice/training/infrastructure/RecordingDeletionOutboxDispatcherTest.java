package org.example.voice.training.infrastructure;

import org.example.voice.training.domain.entity.RecordingDeletionOutbox;
import org.example.voice.training.domain.port.RecordingObjectStoragePort;
import org.example.voice.training.domain.type.RecordingDeletionReason;
import org.example.voice.training.domain.type.RecordingDeletionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordingDeletionOutboxDispatcherTest {

    @Test
    void marksAnIdempotentObjectDeleteAsCompleted() {
        RecordingDeletionOutbox deletion = pending();
        RecordingDeletionOutboxJpaRepository repository = mock(RecordingDeletionOutboxJpaRepository.class);
        RecordingObjectStoragePort storage = mock(RecordingObjectStoragePort.class);
        when(repository.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                eq(RecordingDeletionStatus.PENDING), any()
        )).thenReturn(List.of(deletion));

        new RecordingDeletionOutboxDispatcher(repository, storage).dispatch();

        verify(storage).deleteObject(9L, 7L, deletion.getObjectKey());
        assertThat(deletion.getStatus()).isEqualTo(RecordingDeletionStatus.DELETED);
        assertThat(deletion.getDeletedAt()).isNotNull();
    }

    @Test
    void retainsAStorageFailureForBoundedRetry() {
        RecordingDeletionOutbox deletion = pending();
        RecordingDeletionOutboxJpaRepository repository = mock(RecordingDeletionOutboxJpaRepository.class);
        RecordingObjectStoragePort storage = mock(RecordingObjectStoragePort.class);
        when(repository.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                eq(RecordingDeletionStatus.PENDING), any()
        )).thenReturn(List.of(deletion));
        doThrow(new IllegalStateException("unavailable"))
                .when(storage).deleteObject(9L, 7L, deletion.getObjectKey());

        new RecordingDeletionOutboxDispatcher(repository, storage).dispatch();

        assertThat(deletion.getStatus()).isEqualTo(RecordingDeletionStatus.PENDING);
        assertThat(deletion.getAttemptCount()).isEqualTo(1);
        assertThat(deletion.getLastErrorCode()).isEqualTo("object_storage_delete_failed");
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
