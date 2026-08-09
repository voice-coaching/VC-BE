package org.example.voice.training.domain.port;

public interface AnalysisJobPublisher {

    // 분석 작업 발행 포트.
    // 현재 구현체는 mock 로그만 남기지만, 추후 Redis Queue/RabbitMQ/SQS 등으로 교체할 지점이다.
    void publish(Long analysisId, Long sessionId, Long recordingId);
}
