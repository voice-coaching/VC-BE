package org.example.voice.training.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.entity.RecordingUploadIntent;
import org.example.voice.training.domain.port.RecordingDeletionScheduler;
import org.example.voice.training.domain.port.RecordingUploadIntentRegistry;
import org.example.voice.training.domain.type.RecordingDeletionReason;
import org.example.voice.training.domain.type.RecordingUploadIntentStatus;
import org.example.voice.user.domain.port.UserReader;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
@RequiredArgsConstructor
public class JpaRecordingUploadIntentRegistry implements RecordingUploadIntentRegistry {

    private final RecordingUploadIntentJpaRepository repository;
    private final UserReader userReader;
    private final TrainingSessionJpaRepository trainingSessionRepository;
    private final RecordingDeletionScheduler deletionScheduler;

    @Override
    @Transactional
    public void recordIssued(
            Long userId,
            Long sessionId,
            String objectKey,
            String mimeType,
            Long fileSizeBytes,
            OffsetDateTime expiresAt
    ) {
        repository.save(RecordingUploadIntent.issue(
                userId,
                sessionId,
                objectKey,
                mimeType,
                fileSizeBytes,
                expiresAt
        ));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveForRegistration(
            Long userId,
            Long sessionId,
            String objectKey,
            String mimeType,
            Long fileSizeBytes
    ) {}

    @Override
    @Transactional
    public void markConsumed(Long userId, Long sessionId, String objectKey) {}

    @Override
    @Transactional
    public void expireForSession(Long userId, Long sessionId) {
        repository.findByUserIdAndTrainingSessionIdAndStatus(
                userId,
                sessionId,
                RecordingUploadIntentStatus.ISSUED
        ).forEach(this::scheduleExpiration);
    }

    @Override
    @Transactional
    public void expireForUser(Long userId) {
        repository.findByUserIdAndStatus(userId, RecordingUploadIntentStatus.ISSUED)
                .forEach(this::scheduleExpiration);
    }

    private void scheduleExpiration(RecordingUploadIntent intent) {
        if (!intent.expire()) {
            return;
        }
        deletionScheduler.schedule(
                intent.getUserId(),
                intent.getTrainingSessionId(),
                intent.getObjectKey(),
                RecordingDeletionReason.UPLOAD_EXPIRED
        );
    }
}
