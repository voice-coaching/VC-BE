package org.example.voice.training.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.training.domain.entity.RecordingDeletionOutbox;
import org.example.voice.training.domain.port.RecordingObjectStoragePort;
import org.example.voice.training.domain.type.RecordingDeletionStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class RecordingDeletionOutboxDispatcher {

    private static final int MAX_ATTEMPTS = 10;

    private final RecordingDeletionOutboxJpaRepository repository;
    private final RecordingObjectStoragePort objectStorage;

    @Scheduled(fixedDelayString = "${storage.deletion-dispatch-interval-ms:30000}")
    @Transactional
    public void dispatch() {
        for (RecordingDeletionOutbox deletion : repository
                .findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                        RecordingDeletionStatus.PENDING,
                        OffsetDateTime.now(ZoneOffset.UTC)
                )) {
            try {
                objectStorage.deleteObject(
                        deletion.getUserId(),
                        deletion.getTrainingSessionId(),
                        deletion.getObjectKey()
                );
                deletion.markDeleted();
            } catch (RuntimeException error) {
                deletion.recordFailure("object_storage_delete_failed", MAX_ATTEMPTS);
            }
        }
        repository.deleteByStatusAndDeletedAtBefore(
                RecordingDeletionStatus.DELETED,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)
        );
    }
}
