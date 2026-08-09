package org.example.voice.training.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.domain.type.PublishStatus;
import org.example.voice.practicecontent.infrastructure.PracticeContentJpaRepository;
import org.example.voice.training.domain.entity.TrainingSession;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.example.voice.training.domain.model.TrainingSessionDetailData;
import org.example.voice.training.domain.port.TrainingSessionReader;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TrainingSessionReaderImpl implements TrainingSessionReader {

    private final PracticeContentJpaRepository practiceContentJpaRepository;
    private final TrainingSessionJpaRepository trainingSessionJpaRepository;
    private final VoiceRecordingJpaRepository voiceRecordingJpaRepository;
    private final AnalysisResultJpaRepository analysisResultJpaRepository;

    @Override
    public boolean existsContent(Long contentId) {
        return practiceContentJpaRepository.existsById(contentId);
    }

    @Override
    public boolean existsAvailableContent(Long contentId) {
        return practiceContentJpaRepository.existsByIdAndStatus(contentId, PublishStatus.PUBLISHED);
    }

    @Override
    public Optional<TrainingSessionDetailData> findSessionDetail(Long sessionId, Long userId) {
        return trainingSessionJpaRepository.findByIdAndUserId(sessionId, userId)
                .map(session -> {
                    Long selectedRecordingId = voiceRecordingJpaRepository
                            .findFirstByTrainingSessionIdAndTrainingSessionUserIdAndSelectedTrueAndDeletedAtIsNullOrderByCreatedAtDesc(
                                    sessionId,
                                    userId
                            )
                            .map(VoiceRecording::getId)
                            .orElse(null);
                    int recordingCount = voiceRecordingJpaRepository.countByTrainingSessionIdAndDeletedAtIsNull(sessionId);
                    boolean analysisAvailable = analysisResultJpaRepository
                            .existsByRecordingTrainingSessionIdAndRecordingTrainingSessionUserIdAndRecordingSelectedTrueAndRecordingDeletedAtIsNullAndStatus(
                                    sessionId,
                                    userId,
                                    org.example.voice.analysis.domain.type.AnalysisStatus.COMPLETED
                            );
                    return toDetailData(session, selectedRecordingId, recordingCount, analysisAvailable);
                });
    }

    @Override
    public boolean existsSession(Long sessionId, Long userId) {
        return trainingSessionJpaRepository.existsByIdAndUserId(sessionId, userId);
    }

    @Override
    public Optional<TrainingSessionStatus> findSessionStatus(Long sessionId, Long userId) {
        return trainingSessionJpaRepository.findByIdAndUserId(sessionId, userId)
                .map(TrainingSession::getStatus);
    }

    private TrainingSessionDetailData toDetailData(
            TrainingSession session,
            Long selectedRecordingId,
            Integer recordingCount,
            Boolean analysisAvailable
    ) {
        return new TrainingSessionDetailData(
                session.getId(),
                session.getStatus(),
                new TrainingSessionDetailData.ContentData(
                        session.getContent().getId(),
                        session.getContent().getTitle(),
                        session.getContent().getScriptText()
                ),
                selectedRecordingId,
                recordingCount,
                analysisAvailable,
                session.getStartedAt()
        );
    }
}
