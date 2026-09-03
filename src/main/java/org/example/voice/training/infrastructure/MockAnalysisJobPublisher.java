package org.example.voice.training.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.example.voice.training.domain.model.AnalysisJobRequestData;
import org.example.voice.training.domain.port.AnalysisJobPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "analysis.redis-stream", name = "enabled", havingValue = "false")
public class MockAnalysisJobPublisher implements AnalysisJobPublisher {

    @Override
    public void publish(AnalysisJobRequestData request) {
        log.info("Mock analysis job published. analysisId={}, sessionId={}, recordingId={}",
                request.analysisId(), request.sessionId(), request.recordingId());
    }
}
