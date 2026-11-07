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
        if(result.isIndependentExecution()) return result.getStatus()==AnalysisStatus.COMPLETED
                && result.getCanonicalResultEventId()!=null && result.isCoachingDelivered();
        if(result.getStatus()!=AnalysisStatus.COMPLETED) return false;
        return committedResults.findCurrent(result).filter(doc -> allows(result,doc)).isPresent();
    }

    /** The document must come from the committed reader, never from the preview/spool. */
    public boolean allows(AnalysisResult result, CanonicalCallbackDocument committed) {
        return stored(result,committed) && result.getStatus()==AnalysisStatus.COMPLETED
                && deliverable(committed);
    }

    public static boolean stored(AnalysisResult result, CanonicalCallbackDocument committed) {
        return committed!=null
                && (result.getStatus()==AnalysisStatus.COMPLETED || result.getStatus()==AnalysisStatus.FAILED)
                && result.getStatus()==committed.status()
                && CanonicalResultAttempt.matches(result,committed)
                && committed.identity().eventId().equals(result.getCanonicalResultEventId());
    }

    /** Completion is independent of pronunciation score: an unscorable coaching result can still be delivered. */
    public static boolean deliverable(CanonicalCallbackDocument document) {
        return document.decision()!=null
                && document.decision().status()==CanonicalCallbackDocument.DecisionStatus.ACCEPT
                && "INLINE".equals(document.representation()) && document.feedbackDeliveryAllowed();
    }
}
