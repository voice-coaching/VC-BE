package org.example.voice.training.infrastructure;

import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.Optional;

public interface AnalysisResultJpaRepository extends JpaRepository<AnalysisResult, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AnalysisResult a where a.id = :analysisId")
    Optional<AnalysisResult> findByIdForUpdate(@Param("analysisId") Long analysisId);

    Optional<AnalysisResult> findByIdAndRecordingTrainingSessionUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AnalysisResult a where a.id = :analysisId and a.recording.trainingSession.userId = :userId")
    Optional<AnalysisResult> findOwnedForUpdate(@Param("analysisId") Long analysisId, @Param("userId") Long userId);

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
