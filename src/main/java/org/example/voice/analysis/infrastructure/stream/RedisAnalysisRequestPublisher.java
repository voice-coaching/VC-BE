package org.example.voice.analysis.infrastructure.stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Single external side effect used by the durable outbox dispatcher. */
@Component
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class RedisAnalysisRequestPublisher {

    private static final DefaultRedisScript<String> PUBLISH_ONCE = new DefaultRedisScript<>("""
            local existing = redis.call('GET', KEYS[2])
            if existing then
                return existing
            end
            local streamId = redis.call('XADD', KEYS[1], '*', 'payload', ARGV[1])
            redis.call('SET', KEYS[2], streamId)
            return streamId
            """, String.class);

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

    public String publish(UUID eventId, String payload) {
        if (payload == null
                || payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > properties.getMaximumPayloadBytes()) {
            throw new IllegalArgumentException("analysis_request_payload_size_invalid");
        }
        String streamId = stringRedisTemplate.execute(
                PUBLISH_ONCE,
                List.of(
                        properties.getRequestStream(),
                        properties.getRequestIndexKeyPrefix() + eventId
                ),
                payload
        );
        if (streamId == null || !streamId.matches("[0-9]+-[0-9]+") || streamId.length() > 64) {
            throw new IllegalStateException("analysis_request_publish_unconfirmed");
        }
        metrics.requestPublished();
        return streamId;
    }
}
