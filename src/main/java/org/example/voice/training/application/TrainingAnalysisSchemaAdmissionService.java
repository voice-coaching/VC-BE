package org.example.voice.training.application;

import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.exception.AnalysisSchemaAdmissionException;
import org.springframework.stereotype.Service;
import org.example.voice.analysis.domain.type.AnalysisExecutionProfile;

@Service
public class TrainingAnalysisSchemaAdmissionService {
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalBackendReadiness readiness;
    private final org.example.voice.analysis.domain.port.AnalysisSubmissionAdmission submissions;
    public TrainingAnalysisSchemaAdmissionService(
            org.example.voice.analysis.infrastructure.canonical.CanonicalBackendReadiness readiness,
            org.example.voice.analysis.domain.port.AnalysisSubmissionAdmission submissions) {
        this.readiness=readiness;
        this.submissions=submissions;
    }
    public static final String CANONICAL_RESULT_SCHEMA = "voice-coaching.runpod-analysis-result.v4";

    public AnalysisExecutionProfile selectProfile(String requestedResultSchema) {
        submissions.assertOpen();
        assertAvailable(requestedResultSchema);
        return requestedResultSchema == null ? AnalysisExecutionProfile.LEGACY : AnalysisExecutionProfile.CANONICAL;
    }

    public void assertAvailable(String requestedResultSchema) {
        if (requestedResultSchema == null) return;
        if (!CANONICAL_RESULT_SCHEMA.equals(requestedResultSchema)) {
            throw new AnalysisSchemaAdmissionException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if(!readiness.admissionEnabled())
            throw new AnalysisSchemaAdmissionException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
    }
}
