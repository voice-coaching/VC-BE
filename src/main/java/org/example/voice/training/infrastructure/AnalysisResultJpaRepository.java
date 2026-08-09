package org.example.voice.training.infrastructure;

import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface AnalysisResultJpaRepository extends JpaRepository<AnalysisResult, Long> {

    boolean existsByRecordingIdAndStatusIn(Long recordingId, Collection<AnalysisStatus> statuses);

    boolean existsByRecordingIdAndStatus(Long recordingId, AnalysisStatus status);

    boolean existsByRecordingTrainingSessionIdAndRecordingTrainingSessionUserIdAndRecordingSelectedTrueAndRecordingDeletedAtIsNullAndStatus(
            Long sessionId,
            Long userId,
            AnalysisStatus status
    );

    Optional<AnalysisResult> findFirstByRecordingTrainingSessionIdAndRecordingTrainingSessionUserIdAndRecordingSelectedTrueAndRecordingDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            Long sessionId,
            Long userId
    );

    Optional<AnalysisResult> findFirstByRecordingTrainingSessionIdAndRecordingTrainingSessionUserIdAndRecordingSelectedTrueAndRecordingDeletedAtIsNullAndStatusOrderByCreatedAtDescIdDesc(
            Long sessionId,
            Long userId,
            AnalysisStatus status
    );

    int countByRecordingIdAndStatus(Long recordingId, AnalysisStatus status);
}
