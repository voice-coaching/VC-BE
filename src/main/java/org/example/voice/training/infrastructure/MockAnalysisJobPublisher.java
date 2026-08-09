package org.example.voice.training.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.example.voice.training.domain.port.AnalysisJobPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MockAnalysisJobPublisher implements AnalysisJobPublisher {

    @Override
    public void publish(Long analysisId, Long sessionId, Long recordingId) {
        // AI 명세와 Redis Queue가 확정되기 전까지 사용하는 개발용 구현체다.
        // 실제 연동 시에는 이 클래스 대신 RedisAnalysisJobPublisher 같은 구현체를 만들면 된다.
        log.info("Mock analysis job published. analysisId={}, sessionId={}, recordingId={}",
                analysisId, sessionId, recordingId);
    }
}
