package org.example.voice.analysis.domain.port;

import org.example.voice.analysis.domain.model.CanonicalExecutionBinding;

import java.util.Optional;
import java.util.UUID;

/** Internal persistence only; not wired to admission, callbacks, or public controllers. */
public interface CanonicalExecutionRegistry {
    /** Derives all fields from the persisted current request; identical registration is idempotent. */
    CanonicalExecutionBinding registerCurrent(Long analysisId, UUID requestId, UUID executionId);

    /** A live ownership/current-execution lookup, never a retained-history or result lookup. */
    Optional<CanonicalExecutionBinding> findCurrentForOwner(Long analysisId, Long userId);
}
