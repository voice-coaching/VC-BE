package org.example.voice.analysis.domain.type;

/** Backend delivery/collection state, separate from the AI terminal status and public analysis status. */
public enum IndependentAnalysisStage {
    SUBMIT_PENDING, LOOKUP_PENDING, COLLECTING, COLLECTING_FAILED,
    ACK_PENDING, ACKED, SUBMISSION_REJECTED, CANCELLED, FINISHED
}
