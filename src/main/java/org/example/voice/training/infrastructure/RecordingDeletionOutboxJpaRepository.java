package org.example.voice.training.infrastructure;

import jakarta.persistence.LockModeType;
import org.example.voice.training.domain.entity.RecordingDeletionOutbox;
import org.example.voice.training.domain.type.RecordingDeletionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RecordingDeletionOutboxJpaRepository extends JpaRepository<RecordingDeletionOutbox, Long> {

    boolean existsByObjectKey(String objectKey);

    @Query("""
            select deletion.id from RecordingDeletionOutbox deletion
            where deletion.status = :status and deletion.nextAttemptAt <= :now
            order by deletion.id
            """)
    List<Long> findDispatchCandidateIds(
            @Param("status") RecordingDeletionStatus status,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select deletion from RecordingDeletionOutbox deletion where deletion.id = :id")
    Optional<RecordingDeletionOutbox> findForDeliveryById(@Param("id") Long id);

    long countByStatus(RecordingDeletionStatus status);

    Optional<RecordingDeletionOutbox> findFirstByStatusOrderByCreatedAtAsc(
            RecordingDeletionStatus status
    );

    long deleteByStatusAndDeletedAtBefore(RecordingDeletionStatus status, OffsetDateTime cutoff);
}
