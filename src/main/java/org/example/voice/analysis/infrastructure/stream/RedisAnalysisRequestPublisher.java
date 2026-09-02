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

    public RedisAnalysisRequestPublisher(
            @Qualifier("analysisStreamRedisTemplate") StringRedisTemplate stringRedisTemplate,
            AnalysisStreamProperties properties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    public void publish(String payload) {
        stringRedisTemplate.opsForStream().add(
                StreamRecords.mapBacked(Map.of("payload", payload))
                        .withStreamKey(properties.getRequestStream())
        );
    }
}
