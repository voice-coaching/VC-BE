package org.example.voice.training.infrastructure;

import jakarta.persistence.LockModeType;
import org.example.voice.training.domain.entity.RecordingUploadIntent;
import org.example.voice.training.domain.type.RecordingUploadIntentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface RecordingUploadIntentJpaRepository extends JpaRepository<RecordingUploadIntent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RecordingUploadIntent> findByObjectKeyAndUserIdAndTrainingSessionId(
            String objectKey,
            Long userId,
            Long trainingSessionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RecordingUploadIntent> findByUserIdAndTrainingSessionIdAndStatus(
            Long userId,
            Long trainingSessionId,
            RecordingUploadIntentStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RecordingUploadIntent> findByUserIdAndStatus(Long userId, RecordingUploadIntentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RecordingUploadIntent> findTop100ByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
            RecordingUploadIntentStatus status,
            OffsetDateTime expiresAt
    );

    long deleteByStatusInAndResolvedAtBefore(
            Collection<RecordingUploadIntentStatus> statuses,
            OffsetDateTime cutoff
    );
}
