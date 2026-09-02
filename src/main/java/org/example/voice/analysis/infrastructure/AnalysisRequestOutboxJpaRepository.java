package org.example.voice.analysis.infrastructure;

import jakarta.persistence.LockModeType;
import org.example.voice.analysis.domain.entity.AnalysisRequestOutbox;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.OffsetDateTime;
import java.util.List;

public interface AnalysisRequestOutboxJpaRepository extends JpaRepository<AnalysisRequestOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AnalysisRequestOutbox> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            AnalysisRequestOutboxStatus status,
            OffsetDateTime now
    );
}
