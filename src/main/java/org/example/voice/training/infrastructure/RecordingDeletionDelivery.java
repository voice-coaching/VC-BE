package org.example.voice.training.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.training.domain.entity.RecordingDeletionOutbox;
import org.example.voice.training.domain.port.RecordingObjectStoragePort;
import org.example.voice.training.domain.type.RecordingDeletionStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Delivers exactly one storage delete in its own locked database transaction. */
@Component
@RequiredArgsConstructor
public class RecordingDeletionDelivery {

    private static final int MAX_ATTEMPTS = 10;

    private final RecordingDeletionOutboxJpaRepository repository;
    private final RecordingObjectStoragePort objectStorage;
    private final RecordingDeletionMetrics metrics;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(Long id) {
        RecordingDeletionOutbox deletion = repository.findForDeliveryById(id).orElse(null);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (deletion == null
                || deletion.getStatus() != RecordingDeletionStatus.PENDING
                || deletion.getNextAttemptAt().isAfter(now)) {
            return;
        }
        try {
            objectStorage.deleteObject(
                    deletion.getUserId(),
                    deletion.getTrainingSessionId(),
                    deletion.getObjectKey()
            );
            deletion.markDeleted();
            metrics.deleted();
        } catch (RuntimeException error) {
            if (deletion.recordFailure("object_storage_delete_failed", MAX_ATTEMPTS)) {
                metrics.terminalFailure();
            } else {
                metrics.retryScheduled();
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purgeDeletedBefore(OffsetDateTime cutoff) {
        repository.deleteByStatusAndDeletedAtBefore(RecordingDeletionStatus.DELETED, cutoff);
    }
}
