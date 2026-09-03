package org.example.voice.training.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.example.voice.training.domain.model.AnalysisJobRequestData;
import org.example.voice.training.domain.model.RecordingPlaybackUrlData;
import org.example.voice.training.domain.model.VoiceRecordingData;
import org.example.voice.training.domain.port.VoiceRecordingReader;
import org.example.voice.training.domain.type.RecordingQualityStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoiceRecordingReaderImpl implements VoiceRecordingReader {

    private final VoiceRecordingJpaRepository voiceRecordingJpaRepository;
    private final AnalysisResultJpaRepository analysisResultJpaRepository;
    private final PresignedUrlProvider presignedUrlProvider;

    @Override
    public boolean existsByObjectKey(String objectKey) {
        return voiceRecordingJpaRepository.existsByAudioUrlAndDeletedAtIsNull(objectKey);
    }

    @Override
    public int countBySessionId(Long sessionId) {
        return voiceRecordingJpaRepository.countByTrainingSessionIdAndDeletedAtIsNull(sessionId);
    }

    @Override
    public List<VoiceRecordingData> findBySessionId(Long sessionId, Long userId) {
        return voiceRecordingJpaRepository
                .findByTrainingSessionIdAndTrainingSessionUserIdAndDeletedAtIsNullOrderByAttemptNoAsc(sessionId, userId)
                .stream()
                .map(this::toData)
                .toList();
    }

    @Override
    public Optional<RecordingQualityStatus> findQualityStatus(Long sessionId, Long recordingId, Long userId) {
        return findRecording(sessionId, recordingId, userId)
                .map(VoiceRecording::getQualityStatus);
    }

    @Override
    public boolean isSelected(Long sessionId, Long recordingId, Long userId) {
        return findRecording(sessionId, recordingId, userId)
                .map(VoiceRecording::getSelected)
                .orElse(false);
    }

    @Override
    public boolean hasCompletedAnalysis(Long recordingId) {
        return analysisResultJpaRepository.existsByRecordingIdAndStatus(recordingId, AnalysisStatus.COMPLETED);
    }

    @Override
    public Optional<Long> findSelectedRecordingId(Long sessionId, Long userId) {
        return voiceRecordingJpaRepository
                .findFirstByTrainingSessionIdAndTrainingSessionUserIdAndSelectedTrueAndDeletedAtIsNullOrderByCreatedAtDesc(
                        sessionId,
                        userId
                )
                .map(VoiceRecording::getId);
    }

    @Override
    public Optional<RecordingQualityStatus> findSelectedRecordingQualityStatus(Long sessionId, Long userId) {
        return voiceRecordingJpaRepository
                .findFirstByTrainingSessionIdAndTrainingSessionUserIdAndSelectedTrueAndDeletedAtIsNullOrderByCreatedAtDesc(
                        sessionId,
                        userId
                )
                .map(VoiceRecording::getQualityStatus);
    }

    @Override
    public Optional<RecordingPlaybackUrlData> findPlaybackUrl(Long recordingId, Long userId) {
        return voiceRecordingJpaRepository.findById(recordingId)
                .filter(recording -> recording.getDeletedAt() == null)
                .filter(recording -> recording.getTrainingSession().getUserId().equals(userId))
                .map(recording -> {
                    OffsetDateTime expiresAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(10);
                    return new RecordingPlaybackUrlData(
                            recording.getId(),
                            presignedUrlProvider.createPlaybackUrl(recording.getAudioUrl(), expiresAt),
                            expiresAt
                    );
                });
    }

    @Override
    public Optional<AnalysisJobRequestData> findAnalysisJobRequest(
            Long analysisId,
            Long sessionId,
            Long recordingId,
            Long userId
    ) {
        return findRecording(sessionId, recordingId, userId)
                .map(recording -> new AnalysisJobRequestData(
                        analysisId,
                        sessionId,
                        recordingId,
                        userId,
                        recording.getAudioUrl(),
                        recording.getTrainingSession().getContent().getScriptText(),
                        recording.getTrainingSession().getLearningFocus()
                ));
    }

    private Optional<VoiceRecording> findRecording(Long sessionId, Long recordingId, Long userId) {
        return voiceRecordingJpaRepository.findByIdAndTrainingSessionIdAndTrainingSessionUserIdAndDeletedAtIsNull(
                recordingId,
                sessionId,
                userId
        );
    }

    private VoiceRecordingData toData(VoiceRecording recording) {
        return new VoiceRecordingData(
                recording.getId(),
                recording.getAttemptNo(),
                recording.getDurationMs(),
                recording.getQualityStatus(),
                recording.getSelected()
        );
    }
}
