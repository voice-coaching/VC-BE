package org.example.voice.analysis.domain.model;

/** User-requested analysis retry count, not a transport or GPT retry budget. */
public final class AnalysisRetryPolicy {
    public static final int MAX_RETRY_COUNT = 3;
    private AnalysisRetryPolicy() {}
}
