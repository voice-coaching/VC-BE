package org.example.voice.training.application;

import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.exception.AnalysisSchemaAdmissionException;
import org.springframework.stereotype.Service;
import org.example.voice.analysis.domain.type.AnalysisExecutionProfile;

@Service
public class TrainingAnalysisSchemaAdmissionService {
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalBackendReadiness readiness;
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalHandoffReadiness handoff;
    private final org.example.voice.analysis.domain.port.AnalysisSubmissionAdmission submissions;
    public TrainingAnalysisSchemaAdmissionService(
            org.example.voice.analysis.infrastructure.canonical.CanonicalBackendReadiness readiness,
            org.example.voice.analysis.domain.port.AnalysisSubmissionAdmission submissions,
            org.example.voice.analysis.infrastructure.canonical.CanonicalHandoffReadiness handoff) {
        this.handoff=handoff;
        this.readiness=readiness;
        this.submissions=submissions;
    }
    public static final String CANONICAL_RESULT_SCHEMA = "voice-coaching.runpod-analysis-result.v4";

    public AnalysisExecutionProfile selectProfile(String requestedResultSchema) {
        submissions.assertOpen();
        assertAvailable(requestedResultSchema);
        if(org.example.voice.analysis.infrastructure.runpod.RunPodContract.RESULT_V5.equals(requestedResultSchema))return AnalysisExecutionProfile.CANONICAL_HANDOFF;
        return requestedResultSchema == null ? AnalysisExecutionProfile.LEGACY : AnalysisExecutionProfile.CANONICAL;
    }

    public void assertAvailable(String requestedResultSchema) {
        if (requestedResultSchema == null) return;
        if (org.example.voice.analysis.infrastructure.runpod.RunPodContract.RESULT_V5.equals(requestedResultSchema)) {
            if(!handoff.admission())throw new AnalysisSchemaAdmissionException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
            return;
        }
        if (!CANONICAL_RESULT_SCHEMA.equals(requestedResultSchema)) {
            throw new AnalysisSchemaAdmissionException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if(!readiness.admissionEnabled())
            throw new AnalysisSchemaAdmissionException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
    }
}
