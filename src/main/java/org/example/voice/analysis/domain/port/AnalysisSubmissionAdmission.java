package org.example.voice.analysis.domain.port;

/** External maintenance admission; independent of internal worker readiness. */
public interface AnalysisSubmissionAdmission {
    void assertOpen();
}
