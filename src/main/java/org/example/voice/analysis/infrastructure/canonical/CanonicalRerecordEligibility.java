package org.example.voice.analysis.infrastructure.canonical;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.springframework.stereotype.Component;

/** Shared read/command policy. Re-recording never retries, rewrites or re-grades the old attempt. */
@Component
@RequiredArgsConstructor
public final class CanonicalRerecordEligibility {
    private final CanonicalCommittedResultReader committedResults;
    private final CanonicalRequestScope scope;

    public boolean allowsRecovery(AnalysisResult result) { return true; }

    private static boolean recoverable(CanonicalCallbackDocument document) {

        return switch (document.decision().status()) {
            case REJECT -> "GLOBAL_REJECT".equals(document.adapterStatus());
            case INCONCLUSIVE -> "GLOBAL_INCONCLUSIVE".equals(document.adapterStatus());
            case ACCEPT, SYSTEM_FAILURE -> false;
        };
    }
}
