package org.example.voice.training.application;

import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.exception.AnalysisSchemaAdmissionException;
import org.example.voice.analysis.domain.type.AnalysisExecutionProfile;
import org.example.voice.analysis.domain.port.AnalysisSubmissionAdmission;
import org.example.voice.analysis.infrastructure.canonical.CanonicalHandoffReadiness;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.springframework.stereotype.Service;

/** New executions use v6 for both audio and video; historical readers are independent. */
@Service
public class TrainingAnalysisSchemaAdmissionService {
    private final CanonicalHandoffReadiness handoff;
    private final AnalysisSubmissionAdmission submissions;
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalAudiovisualReadiness audiovisual;
    private final org.example.voice.analysis.infrastructure.independent.IndependentAnalysisProperties independent;
    public static final String CANONICAL_RESULT_SCHEMA = RunPodContract.RESULT_V6;
    public TrainingAnalysisSchemaAdmissionService(AnalysisSubmissionAdmission submissions,
                                                  CanonicalHandoffReadiness handoff,
                                                  org.example.voice.analysis.infrastructure.canonical.CanonicalAudiovisualReadiness audiovisual) {
        this(submissions,handoff,audiovisual,new org.example.voice.analysis.infrastructure.independent.IndependentAnalysisProperties());
    }
    @org.springframework.beans.factory.annotation.Autowired
    public TrainingAnalysisSchemaAdmissionService(AnalysisSubmissionAdmission submissions,
            CanonicalHandoffReadiness handoff,org.example.voice.analysis.infrastructure.canonical.CanonicalAudiovisualReadiness audiovisual,
            org.example.voice.analysis.infrastructure.independent.IndependentAnalysisProperties independent) {
        this.handoff = handoff;
        this.submissions = submissions;
        this.audiovisual = audiovisual;
        this.independent = independent;
    }
    public AnalysisExecutionProfile selectProfile(String requestedResultSchema) {
        if(independent.isSubmissionEnabled()) return AnalysisExecutionProfile.INDEPENDENT_PULL;
        return AnalysisExecutionProfile.CANONICAL_AUDIOVISUAL;
    }
    public void assertAvailable(String requestedResultSchema) {
        assertAvailable(requestedResultSchema, false);
    }
    public void assertAvailable(String requestedResultSchema, boolean video) {
        submissions.assertOpen();
        if(independent.isSubmissionEnabled()) {
            if(video || !independent.configured()) throw new AnalysisSchemaAdmissionException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
            return;
        }
        if (!handoff.admission() || (video && !audiovisual.admission()))
            throw new AnalysisSchemaAdmissionException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
    }
}
