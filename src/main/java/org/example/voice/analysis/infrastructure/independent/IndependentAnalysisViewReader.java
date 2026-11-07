package org.example.voice.analysis.infrastructure.independent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.CanonicalAnalysisView;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.exception.CanonicalAnalysisViewException;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import java.util.List;
import java.util.Set;
import static org.example.voice.analysis.exception.CanonicalAnalysisViewException.Reason.*;

/** Project only the current stored independent result; no remote I/O or legacy envelope parsing. */
@Component @RequiredArgsConstructor
public class IndependentAnalysisViewReader {
    private final IndependentAnalysisQueue queue;
    public CanonicalAnalysisView read(AnalysisResult result,long ownerId) {
        var attempt=queue.current(result.getId(),ownerId,result.getActiveRequestEventId(),result.getActiveExecutionId())
                .orElseThrow(()->new CanonicalAnalysisViewException(CANONICAL_RESULT_UNAVAILABLE));
        boolean terminal=result.getStatus()==AnalysisStatus.COMPLETED || result.getStatus()==AnalysisStatus.FAILED;
        boolean committed=Set.of("ACK_PENDING","ACKED","FINISHED").contains(attempt.stage());
        CanonicalAnalysisView.Canonical canonical=null;
        if(terminal && committed && attempt.resultBytes()!=null) {
            try {
                canonical=IndependentResultDocument.parse(attempt.resultBytes(),attempt).project();
                if(result.getStatus()==AnalysisStatus.FAILED && (canonical.decision()==null || canonical.score()==null
                        || canonical.score().validity()==null || canonical.score().reason()==null)) canonical=null;
            }
            catch(RuntimeException invalid) { throw new CanonicalAnalysisViewException(CANONICAL_RESULT_UNAVAILABLE); }
        }
        if(result.getStatus()==AnalysisStatus.COMPLETED && canonical==null)
            throw new CanonicalAnalysisViewException(CANONICAL_RESULT_UNAVAILABLE);
        var failure=result.getStatus()==AnalysisStatus.FAILED?new CanonicalAnalysisView.ServiceFailure(
                "AI","INDEPENDENT_ANALYSIS_FAILED","EXECUTION"):null;
        boolean complete=result.getStatus()==AnalysisStatus.COMPLETED && result.isCoachingDelivered();
        boolean retry=result.getStatus()==AnalysisStatus.FAILED;
        var actions=new CanonicalAnalysisView.Actions(retry,terminal,complete,false,
                new CanonicalAnalysisView.UnavailableReasons(retry?List.of():List.of("ANALYSIS_NOT_FAILED"),
                        terminal?List.of():List.of("ANALYSIS_PENDING"),complete?List.of():List.of("ANALYSIS_NOT_COMPLETED"),
                        List.of("INDEPENDENT_FEEDBACK_IMMUTABLE")));
        // Existing public view/profile and Backend IDs are retained; the AI wire contract stays v1.
        return new CanonicalAnalysisView("voice-coaching.canonical-analysis-view.v3",result.getId(),attempt.recordingId(),
                attempt.clientRequestId(),attempt.backendExecutionId(),result.getStatus(),RunPodContract.AUDIOVISUAL_PROFILE,
                canonical,failure,actions,terminal?"SAVED":"NONE");
    }
}
