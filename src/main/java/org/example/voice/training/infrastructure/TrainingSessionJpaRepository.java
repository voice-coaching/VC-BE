package org.example.voice.training.infrastructure;

import org.example.voice.training.domain.entity.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrainingSessionJpaRepository extends JpaRepository<TrainingSession, Long> {

    boolean existsByIdAndUserId(Long id, Long userId);

    Optional<TrainingSession> findByIdAndUserId(Long id, Long userId);
}
