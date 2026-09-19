package org.example.voice.analysis.domain.type;

/**
 * Learner-facing outcome of a terminal AI analysis.
 *
 * <p>This is intentionally separate from {@link AnalysisStatus}. A worker can
 * complete processing while safely declining to issue normal coaching when its
 * evidence gate is not satisfied.</p>
 */
public enum AnalysisOutcome {
    COACHING_READY,
    COMPLETED_NO_ISSUE,
    RERECORD_REQUIRED,
    UNCERTAIN,
    FAILED_CLOSED
}
