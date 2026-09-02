package org.example.voice.analysis.domain.type;

/** Durable delivery state for a request-generation cancellation tombstone. */
public enum AnalysisCancellationOutboxStatus {
    PENDING,
    PUBLISHED
}
