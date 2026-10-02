package org.example.voice.analysis.infrastructure.canonical;

/**
 * Private failure classification shared by storage, offline verification and the
 * worker. Never attach SDK/process messages, causes, credentials or raw evidence.
 * Existing reason codes and retry decisions are preserved; this is not an HTTP DTO.
 */
public final class EvidenceFailure extends RuntimeException {
    private final boolean retryable;

    public EvidenceFailure(boolean retryable) {
        super(retryable ? "EVIDENCE_STORAGE_UNAVAILABLE" : "EVIDENCE_INVALID");
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
