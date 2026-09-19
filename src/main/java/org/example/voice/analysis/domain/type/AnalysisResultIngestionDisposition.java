package org.example.voice.analysis.domain.type;

/** Whether one result stream event changed the current analysis attempt. */
public enum AnalysisResultIngestionDisposition {
    APPLIED,
    IGNORED_DUPLICATE,
    IGNORED_STALE
}
