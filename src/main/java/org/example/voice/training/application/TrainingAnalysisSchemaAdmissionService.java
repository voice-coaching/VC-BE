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
    public static final String CANONICAL_RESULT_SCHEMA = RunPodContract.RESULT_V6;
    public TrainingAnalysisSchemaAdmissionService(AnalysisSubmissionAdmission submissions,
                                                  CanonicalHandoffReadiness handoff,
                                                  org.example.voice.analysis.infrastructure.canonical.CanonicalAudiovisualReadiness audiovisual) {
        this.handoff = handoff;
        this.submissions = submissions;
        this.audiovisual = audiovisual;
    }
    public AnalysisExecutionProfile selectProfile(String requestedResultSchema) {

        return AnalysisExecutionProfile.CANONICAL_AUDIOVISUAL;
    }
    public void assertAvailable(String requestedResultSchema) {}
    public void assertAvailable(String requestedResultSchema, boolean video) {}
}
