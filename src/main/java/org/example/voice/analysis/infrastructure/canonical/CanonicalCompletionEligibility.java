package org.example.voice.analysis.infrastructure.canonical;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.springframework.stereotype.Component;

/** Learning-procedure completion, never a pronunciation/score/title judgement. */
@Component
@RequiredArgsConstructor
public final class CanonicalCompletionEligibility {
    private final CanonicalCommittedResultReader committedResults;

    public boolean allows(AnalysisResult result) {
        if (!result.isCanonicalExecution() || result.getStatus()!=AnalysisStatus.COMPLETED) return false;
        return committedResults.findCurrent(result).map(CanonicalCompletionEligibility::allows).orElse(false);
    }

    /** Only used after the current committed-result reader has checked persistence proof. */
    private static boolean allows(CanonicalCallbackDocument document) {
        if (document.status()!=AnalysisStatus.COMPLETED || document.decision()==null
                || document.decision().status()!=CanonicalCallbackDocument.DecisionStatus.ACCEPT
                || !"INLINE".equals(document.representation()) || !document.feedbackDeliveryAllowed()
                || document.retention()==null || document.failure()!=null) return false;
        return ("READY".equals(document.adapterStatus())
                && ("SCHEMA_AND_STRUCTURAL_SEMANTICS_VALID".equals(document.generationStatus())
                    || "DETERMINISTIC_FALLBACK".equals(document.generationStatus())))
                || ("NO_PERMITTED_COACHING_CONTENT".equals(document.adapterStatus())
                    && "NOT_DISPATCHED".equals(document.generationStatus()));
    }
}
