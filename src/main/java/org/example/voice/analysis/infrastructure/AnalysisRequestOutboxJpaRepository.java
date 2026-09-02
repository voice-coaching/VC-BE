package org.example.voice.analysis.infrastructure;

import jakarta.persistence.LockModeType;
import org.example.voice.analysis.domain.entity.AnalysisRequestOutbox;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisRequestOutboxJpaRepository extends JpaRepository<AnalysisRequestOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AnalysisRequestOutbox> findFirstByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            AnalysisRequestOutboxStatus status,
            OffsetDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AnalysisRequestOutbox> findByAnalysisResultIdAndStatus(
            Long analysisId,
            AnalysisRequestOutboxStatus status
    );

    List<AnalysisRequestOutbox> findByAnalysisResultIdOrderByIdAsc(Long analysisId);

    long countByStatus(AnalysisRequestOutboxStatus status);

    Optional<AnalysisRequestOutbox> findFirstByStatusOrderByCreatedAtAsc(
            AnalysisRequestOutboxStatus status
    );

    @Query("""
            select outbox.id from AnalysisRequestOutbox outbox
            where outbox.retentionProtocolVersion = :protocolVersion
              and outbox.status in :outboxStatuses
              and outbox.analysisResult.status in :analysisStatuses
              and outbox.createdAt <= :cutoff
              and not exists (
                  select cancellation.id from AnalysisCancellationOutbox cancellation
                  where cancellation.requestEventId = outbox.eventId
                    and cancellation.status = :pendingCancellationStatus
              )
            order by outbox.id
            """)
    List<Long> findRetentionCandidateIds(
            @Param("protocolVersion") Integer protocolVersion,
            @Param("outboxStatuses") Collection<AnalysisRequestOutboxStatus> outboxStatuses,
            @Param("analysisStatuses") Collection<AnalysisStatus> analysisStatuses,
            @Param("pendingCancellationStatus") org.example.voice.analysis.domain.type.AnalysisCancellationOutboxStatus pendingCancellationStatus,
            @Param("cutoff") OffsetDateTime cutoff,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select outbox from AnalysisRequestOutbox outbox where outbox.id = :id")
    Optional<AnalysisRequestOutbox> findForRetentionById(@Param("id") Long id);
}
