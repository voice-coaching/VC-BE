package org.example.voice.analysis.domain.port;

/** Ends queued/running AI work and removes derived evidence for a canceled session. */
public interface AnalysisCancellation {
    void cancelForSession(Long sessionId);
}
