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
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.example.voice.training.domain.model.RecordingUploadIntentData;

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
    public Optional<Long> reserveForRegistration(
            Long userId,
            Long sessionId,
            String objectKey,
            String mimeType,
            Long fileSizeBytes,
            String registrationSha256
    ) {
        var session = trainingSessionRepository.findByIdForUpdate(sessionId)
                .filter(s -> userId.equals(s.getUserId()))
                .orElseThrow(() -> new BaseException(ErrorCode.TRAINING_SESSION_NOT_FOUND));
        var intent = repository.findByObjectKeyAndUserIdAndTrainingSessionId(objectKey, userId, sessionId)
                .orElseThrow(() -> new BaseException(ErrorCode.UPLOAD_INTENT_NOT_FOUND));
        if (!intent.matchesDeclaredMedia(mimeType, fileSizeBytes))
            throw new BaseException(ErrorCode.ANALYSIS_SOURCE_NOT_READY);
        if (intent.getStatus() == RecordingUploadIntentStatus.CONSUMED) {
            if (intent.getRecordingId() == null || !registrationSha256.equals(intent.getRegistrationSha256()))
                throw new BaseException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            return Optional.of(intent.getRecordingId());
        }
        if (!intent.canRegisterAt(OffsetDateTime.now(ZoneOffset.UTC)))
            throw new BaseException(ErrorCode.UPLOAD_INTENT_NOT_ACTIVE);
        if (session.getStatus() == org.example.voice.training.domain.type.TrainingSessionStatus.COMPLETED
                || session.getStatus() == org.example.voice.training.domain.type.TrainingSessionStatus.CANCELED)
            throw new BaseException(ErrorCode.SESSION_ALREADY_FINISHED);
        return Optional.empty();
    }

    @Override
    @Transactional
    public void markConsumed(Long userId, Long sessionId, String objectKey, Long recordingId, String registrationSha256) {
        repository.findByObjectKeyAndUserIdAndTrainingSessionId(objectKey, userId, sessionId)
                .orElseThrow(() -> new BaseException(ErrorCode.UPLOAD_INTENT_NOT_FOUND))
                .consume(recordingId, registrationSha256);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecordingUploadIntentData> find(Long userId, Long sessionId, String objectKey) {
        return repository.findOwned(userId, sessionId, objectKey).map(this::data);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecordingUploadIntentData> recent(Long userId, Long sessionId, int limit) {
        return repository.findByUserIdAndTrainingSessionIdOrderByIdDesc(userId, sessionId, PageRequest.of(0, limit))
                .stream().map(this::data).toList();
    }

    private RecordingUploadIntentData data(RecordingUploadIntent i) {
        return new RecordingUploadIntentData(i.getId(), i.getObjectKey(), i.getMimeType(), i.getFileSizeBytes(),
                i.getExpiresAt(), i.getStatus(), i.getRecordingId());
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
