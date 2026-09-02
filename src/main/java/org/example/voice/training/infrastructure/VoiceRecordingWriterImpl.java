package org.example.voice.training.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.entity.TrainingSession;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.example.voice.training.domain.model.RecordingSelectionData;
import org.example.voice.training.domain.model.NormalizedRecordingData;
import org.example.voice.training.domain.model.VoiceRecordingRegisteredData;
import org.example.voice.training.domain.port.VoiceRecordingWriter;
import org.example.voice.training.domain.port.RecordingDeletionScheduler;
import org.example.voice.training.domain.type.RecordingDeletionReason;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Repository
@RequiredArgsConstructor
public class VoiceRecordingWriterImpl implements VoiceRecordingWriter {

    private final TrainingSessionJpaRepository trainingSessionJpaRepository;
    private final VoiceRecordingJpaRepository voiceRecordingJpaRepository;
    private final RecordingDeletionScheduler recordingDeletionScheduler;

    @Override
    @Transactional
    public VoiceRecordingRegisteredData register(
            Long sessionId,
            NormalizedRecordingData normalized
    ) {
        TrainingSession session = findMutableSession(sessionId);
        int attemptNo = voiceRecordingJpaRepository.findMaxAttemptNoByTrainingSessionId(sessionId) + 1;
        VoiceRecording recording = voiceRecordingJpaRepository.save(
                VoiceRecording.create(
                        session,
                        attemptNo,
                        normalized.objectKey(),
                        normalized.mimeType(),
                        normalized.fileSizeBytes(),
                        normalized.durationMs(),
                        normalized.audioSha256(),
                        normalized.qualityStatus(),
                        normalized.volumeScore(),
                        normalized.noiseScore()
                )
        );
        return new VoiceRecordingRegisteredData(
                recording.getId(),
                recording.getAttemptNo(),
                recording.getQualityStatus(),
                recording.getSelected(),
                recording.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public RecordingSelectionData select(Long sessionId, Long recordingId) {
        // 한 세션에서는 최종 녹음이 하나만 선택되어야 한다.
        // 먼저 같은 세션의 선택을 모두 해제하고, 요청받은 녹음 하나만 다시 선택한다.
        TrainingSession session = findMutableSession(sessionId);
        voiceRecordingJpaRepository
                .findByTrainingSessionIdAndTrainingSessionUserIdAndDeletedAtIsNullOrderByAttemptNoAsc(
                        sessionId,
                        session.getUserId()
                )
                .forEach(VoiceRecording::unselect);

        VoiceRecording recording = voiceRecordingJpaRepository.findById(recordingId)
                .orElseThrow(() -> new BaseException(ErrorCode.RECORDING_NOT_FOUND));
        if (!recording.getTrainingSession().getId().equals(sessionId)) {
            throw new BaseException(ErrorCode.RECORDING_NOT_FOUND);
        }
        recording.select();
        return new RecordingSelectionData(
                sessionId,
                recordingId,
                OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        );
    }

    @Override
    @Transactional
    public void delete(Long sessionId, Long recordingId) {
        // 실제 파일/row를 즉시 제거하지 않고 deleted_at만 찍는 soft delete 방식이다.
        // 추후 스토리지 삭제 worker가 deleted_at 기준으로 물리 파일을 정리할 수 있다.
        findMutableSession(sessionId);
        VoiceRecording recording = voiceRecordingJpaRepository.findById(recordingId)
                .orElseThrow(() -> new BaseException(ErrorCode.RECORDING_NOT_FOUND));
        if (!recording.getTrainingSession().getId().equals(sessionId)) {
            throw new BaseException(ErrorCode.RECORDING_NOT_FOUND);
        }
        recording.delete();
        recordingDeletionScheduler.schedule(
                recording.getTrainingSession().getUserId(),
                sessionId,
                recording.getAudioUrl(),
                RecordingDeletionReason.RECORDING_DELETED
        );
    }

    private TrainingSession findMutableSession(Long sessionId) {
        TrainingSession session = trainingSessionJpaRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!session.allowsRecordingChanges()) {
            throw new BaseException(ErrorCode.INVALID_SESSION_STATE);
        }
        return session;
    }
}
