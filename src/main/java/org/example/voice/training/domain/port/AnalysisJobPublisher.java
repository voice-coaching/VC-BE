package org.example.voice.training.domain.port;

import org.example.voice.training.domain.model.AnalysisJobRequestData;

public interface AnalysisJobPublisher {

    void publish(AnalysisJobRequestData request);
}
