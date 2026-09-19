package org.example.voice.analysis.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisCancellationOutbox;
import org.example.voice.analysis.domain.port.AnalysisCancellationSignal;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaAnalysisCancellationSignal implements AnalysisCancellationSignal {

    private final AnalysisCancellationOutboxJpaRepository repository;

    @Override
    public void schedule(UUID requestEventId) {
        String value = requestEventId.toString();
        if (!repository.existsByRequestEventId(value)) {
            repository.save(AnalysisCancellationOutbox.pending(requestEventId));
        }
    }
}
