package org.example.voice.training.infrastructure;

import org.example.voice.training.domain.entity.TrainingSession;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TrainingSessionJpaRepository extends JpaRepository<TrainingSession, Long> {

    boolean existsByIdAndUserId(Long id, Long userId);

    Optional<TrainingSession> findByIdAndUserId(Long id, Long userId);

    int countByUserIdAndStatusAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            Long userId,
            TrainingSessionStatus status,
            OffsetDateTime startOfDay,
            OffsetDateTime startOfNextDay
    );

    @Query("""
            select coalesce(sum(ts.totalLearningSeconds), 0)
            from TrainingSession ts
            where ts.userId = :userId
              and ts.startedAt >= :startOfDay
              and ts.startedAt < :startOfNextDay
            """)
    Integer sumLearningSeconds(Long userId, OffsetDateTime startOfDay, OffsetDateTime startOfNextDay);

    @Query("""
            select ts
            from TrainingSession ts
            where ts.userId = :userId
            order by coalesce(ts.completedAt, ts.startedAt) desc
            """)
    List<TrainingSession> findLatestByUserId(Long userId, Pageable pageable);
}
