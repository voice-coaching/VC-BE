package org.example.voice.analysis.domain.port;

import java.util.UUID;

/** Persists an idempotent cancellation signal for one request generation. */
public interface AnalysisCancellationSignal {
    void schedule(UUID requestEventId);
}
