package org.example.voice.training.domain.port;

import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;

public interface AnalysisJobPublisher {

    /** Persist a request for at-least-once delivery to the AI request stream. */
    void publish(AnalysisWorkerRequest request);
}
