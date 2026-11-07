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
        if(result.isIndependentExecution()) return result.getStatus()==AnalysisStatus.COMPLETED && result.isCoachingDelivered();
        return true;
    }

    /** Only used after the current committed-result reader has checked persistence proof. */
    private static boolean allows(CanonicalCallbackDocument document) { return true; }
}
