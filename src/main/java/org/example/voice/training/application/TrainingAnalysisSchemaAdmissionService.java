package org.example.voice.training.application;

import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.exception.AnalysisSchemaAdmissionException;
import org.example.voice.analysis.domain.type.AnalysisExecutionProfile;
import org.example.voice.analysis.domain.port.AnalysisSubmissionAdmission;
import org.example.voice.analysis.infrastructure.canonical.CanonicalHandoffReadiness;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.springframework.stereotype.Service;

/** The sole deployed analysis contract. An absent header also means v5. */
@Service
public class TrainingAnalysisSchemaAdmissionService {
    private final CanonicalHandoffReadiness handoff;
    private final AnalysisSubmissionAdmission submissions;
    public static final String CANONICAL_RESULT_SCHEMA = RunPodContract.RESULT_V5;
    public TrainingAnalysisSchemaAdmissionService(AnalysisSubmissionAdmission submissions,
                                                  CanonicalHandoffReadiness handoff) {
        this.handoff = handoff;
        this.submissions = submissions;
    }
    public AnalysisExecutionProfile selectProfile(String requestedResultSchema) {
        submissions.assertOpen();
        assertAvailable(requestedResultSchema);
        return AnalysisExecutionProfile.CANONICAL_HANDOFF;
    }
    public void assertAvailable(String requestedResultSchema) {
        if (requestedResultSchema != null && !RunPodContract.RESULT_V5.equals(requestedResultSchema))
            throw new AnalysisSchemaAdmissionException(ErrorCode.INVALID_INPUT_VALUE);
        if (!handoff.admission())
            throw new AnalysisSchemaAdmissionException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
    }
}
