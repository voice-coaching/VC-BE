package org.example.voice.analysis.infrastructure.stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Single external side effect used by the durable outbox dispatcher. */
@Component
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class RedisAnalysisRequestPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final AnalysisStreamProperties properties;
    private final AnalysisStreamMetrics metrics;

    public RedisAnalysisRequestPublisher(
            @Qualifier("analysisStreamRedisTemplate") StringRedisTemplate stringRedisTemplate,
            AnalysisStreamProperties properties,
            AnalysisStreamMetrics metrics
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    public void publish(String payload) {
        if (payload == null
                || payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > properties.getMaximumPayloadBytes()) {
            throw new IllegalArgumentException("analysis_request_payload_size_invalid");
        }
        stringRedisTemplate.opsForStream().add(
                StreamRecords.mapBacked(Map.of("payload", payload))
                        .withStreamKey(properties.getRequestStream())
        );
        metrics.requestPublished();
    }
}
