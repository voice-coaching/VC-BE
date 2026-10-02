package org.example.voice.analysis.domain.type;

/** Internal routing intent, not a field added to the legacy Redis/public request DTO. */
public enum AnalysisExecutionProfile {
    LEGACY,
    CANONICAL
}
