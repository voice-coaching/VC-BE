package org.example.voice.analysis.infrastructure.canonical;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.CanonicalAnalysisView.Actions;
import org.example.voice.analysis.domain.model.CanonicalAnalysisView.UnavailableReasons;
import org.springframework.stereotype.Component;

import java.util.List;

/** Read-only UI hints. The actual commands still acquire locks and repeat authorization/state checks. */
@Component
@RequiredArgsConstructor
public final class CanonicalActionPolicy {
    private final CanonicalCompletionEligibility completion;

    public Actions current(AnalysisResult result) {
        return actions(completion.allows(result));
    }

    public Actions current(AnalysisResult result, CanonicalCallbackDocument committed) {
        return actions(completion.allows(result,committed));
    }

    private Actions actions(boolean canComplete) {
        return new Actions(true, true, canComplete, true,
                new UnavailableReasons(List.of(), List.of(),
                        canComplete?List.of():List.of("ANALYSIS_NOT_COMPLETED"), List.of()));
    }
}
