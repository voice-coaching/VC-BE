package org.example.voice.training.infrastructure;

import jakarta.persistence.LockModeType;
import org.example.voice.training.domain.entity.RecordingDeletionOutbox;
import org.example.voice.training.domain.type.RecordingDeletionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.OffsetDateTime;
import java.util.List;

public interface RecordingDeletionOutboxJpaRepository extends JpaRepository<RecordingDeletionOutbox, Long> {

    boolean existsByObjectKey(String objectKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RecordingDeletionOutbox> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            RecordingDeletionStatus status,
            OffsetDateTime now
    );

    long deleteByStatusAndDeletedAtBefore(RecordingDeletionStatus status, OffsetDateTime cutoff);
}
