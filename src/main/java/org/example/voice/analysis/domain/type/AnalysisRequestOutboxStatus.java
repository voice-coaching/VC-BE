package org.example.voice.analysis.domain.type;

/** Lifecycle of a durable Backend-to-AI dispatch record. */
public enum AnalysisRequestOutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
