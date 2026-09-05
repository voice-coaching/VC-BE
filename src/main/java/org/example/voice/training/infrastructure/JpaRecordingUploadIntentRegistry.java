package org.example.voice.training.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.entity.RecordingUploadIntent;
import org.example.voice.training.domain.port.RecordingDeletionScheduler;
import org.example.voice.training.domain.port.RecordingUploadIntentRegistry;
import org.example.voice.training.domain.type.RecordingDeletionReason;
import org.example.voice.training.domain.type.RecordingUploadIntentStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Repository
@RequiredArgsConstructor
public class JpaRecordingUploadIntentRegistry implements RecordingUploadIntentRegistry {

    private final RecordingUploadIntentJpaRepository repository;
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
    @Transactional
    public void markConsumed(Long userId, Long sessionId, String objectKey) {
        RecordingUploadIntent intent = repository.findByObjectKeyAndUserIdAndTrainingSessionId(
                        objectKey,
                        userId,
                        sessionId
                )
                .orElseThrow(() -> new BaseException(ErrorCode.UPLOAD_INTENT_NOT_FOUND));
        if (intent.getStatus() != RecordingUploadIntentStatus.ISSUED) {
            throw new BaseException(ErrorCode.UPLOAD_INTENT_NOT_ACTIVE);
        }
        intent.consume();
    }

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
