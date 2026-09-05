package org.example.voice.training.domain.port;

/** Serializes per-user admission and rejects excess concurrent AI jobs. */
public interface AnalysisAdmissionGuard {
    void acquireAndAssertAvailable(Long userId);
}
