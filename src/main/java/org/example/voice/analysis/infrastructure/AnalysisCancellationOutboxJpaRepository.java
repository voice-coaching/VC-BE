package org.example.voice.analysis.infrastructure;

import jakarta.persistence.LockModeType;
import org.example.voice.analysis.domain.entity.AnalysisCancellationOutbox;
import org.example.voice.analysis.domain.type.AnalysisCancellationOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface AnalysisCancellationOutboxJpaRepository
        extends JpaRepository<AnalysisCancellationOutbox, Long> {

    boolean existsByRequestEventId(String requestEventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AnalysisCancellationOutbox> findFirstByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            AnalysisCancellationOutboxStatus status,
            OffsetDateTime now
    );
}
