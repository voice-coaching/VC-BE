package org.example.voice.analysis.infrastructure.canonical;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.AnalysisRetryPolicy;
import org.example.voice.analysis.domain.model.CanonicalAnalysisView.Actions;
import org.example.voice.analysis.domain.model.CanonicalAnalysisView.UnavailableReasons;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.infrastructure.runpod.RunPodAnalysisProperties;
import org.example.voice.analysis.infrastructure.stream.AnalysisStreamProperties;
import org.example.voice.training.application.TrainingAnalysisSchemaAdmissionService;
import org.example.voice.training.exception.AnalysisSchemaAdmissionException;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Read-only UI hints. The actual commands still acquire locks and repeat authorization/state checks. */
@Component
@RequiredArgsConstructor
public final class CanonicalActionPolicy {
    private final CanonicalCompletionEligibility completion;
    private final CanonicalRerecordEligibility rerecordEligibility;
    private final TrainingAnalysisSchemaAdmissionService admission;
    private final CanonicalRequestScope scope;
    private final AnalysisResultJpaRepository results;
    private final AnalysisStreamProperties concurrency;
    private final RunPodAnalysisProperties runpod;

    public Actions current(AnalysisResult result) {
        var session=result.getRecording().getTrainingSession();
        var retry=new ArrayList<String>();
        if (result.getStatus()!=AnalysisStatus.FAILED) retry.add("ANALYSIS_NOT_FAILED");
        if (result.getRetryCount()>=AnalysisRetryPolicy.MAX_RETRY_COUNT) retry.add("MAX_RETRY_EXCEEDED");
        if (!session.allowsAnalysisRetry()) retry.add("INVALID_SESSION_STATE");
        if (!scope.eligible(result)) retry.add("CANONICAL_SCOPE_UNAVAILABLE");
        try { admission.assertAvailable(result.getExpectedResultSchemaVersion()); }
        catch(AnalysisSchemaAdmissionException error) { retry.add(error.getErrorCode().name()); }
        if (!runpod.isConfigured() && !retry.contains("ANALYSIS_INTEGRATION_UNAVAILABLE")) retry.add("ANALYSIS_INTEGRATION_UNAVAILABLE");
        if (results.countByRecordingTrainingSessionUserIdAndStatusIn(session.getUserId(),
                List.of(AnalysisStatus.PENDING,AnalysisStatus.PROCESSING))>=concurrency.getMaxConcurrentPerUser())
            retry.add("ANALYSIS_CONCURRENT_LIMIT_EXCEEDED");

        var complete=new ArrayList<String>();
        if (session.getStatus()!=TrainingSessionStatus.ANALYZING && session.getStatus()!=TrainingSessionStatus.COMPLETED)
            complete.add("INVALID_SESSION_STATE");
        if (!completion.allows(result)) complete.add("ANALYSIS_NOT_COMPLETED");

        // The upload-url command repeats this recovery policy under session/analysis locks.
        boolean terminal=result.getStatus()==AnalysisStatus.COMPLETED || result.getStatus()==AnalysisStatus.FAILED;
        boolean canRerecord=terminal && (session.allowsRecordingChanges() || rerecordEligibility.allowsRecovery(result));
        var rerecord=canRerecord ? List.<String>of()
                : List.of(!terminal?"ANALYSIS_ALREADY_RUNNING"
                        :session.getStatus()==TrainingSessionStatus.ANALYZING?"CANONICAL_RERECORD_NOT_APPLICABLE":"INVALID_SESSION_STATE");
        return new Actions(retry.isEmpty(),rerecord.isEmpty(),complete.isEmpty(),false,
                new UnavailableReasons(List.copyOf(retry),rerecord,List.copyOf(complete),List.of("FEEDBACK_EVIDENCE_UNAVAILABLE")));
    }
}
