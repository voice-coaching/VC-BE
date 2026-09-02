package org.example.voice.training.infrastructure;

import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface AnalysisResultJpaRepository extends JpaRepository<AnalysisResult, Long> {

    Optional<AnalysisResult> findByIdAndRecordingTrainingSessionUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AnalysisResult a where a.id = :analysisId and a.recording.trainingSession.userId = :userId")
    Optional<AnalysisResult> findOwnedForUpdate(@Param("analysisId") Long analysisId, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AnalysisResult a where a.id = :analysisId")
    Optional<AnalysisResult> findForIngestion(@Param("analysisId") Long analysisId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from AnalysisResult a
            where a.recording.trainingSession.id = :sessionId
              and a.status <> :failedStatus
            order by a.id
            """)
    List<AnalysisResult> findCancelableForUpdate(
            @Param("sessionId") Long sessionId,
            @Param("failedStatus") AnalysisStatus failedStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from AnalysisResult a
            where a.status in :statuses
              and a.analyzedAt <= :cutoff
            order by a.analyzedAt, a.id
            """)
    List<AnalysisResult> findStaleForUpdate(
            @Param("statuses") Collection<AnalysisStatus> statuses,
            @Param("cutoff") OffsetDateTime cutoff,
            Pageable pageable
    );

    long countByRecordingTrainingSessionUserIdAndStatusIn(
            Long userId,
            Collection<AnalysisStatus> statuses
    );

    boolean existsByRecordingIdAndStatusIn(Long recordingId, Collection<AnalysisStatus> statuses);

    boolean existsByRecordingIdAndStatus(Long recordingId, AnalysisStatus status);

    boolean existsByRecordingId(Long recordingId);

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

}
