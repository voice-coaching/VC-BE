package org.example.voice.analysis.infrastructure.stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Readiness signal for the dedicated analysis transport, without exposing failure details. */
@Component("analysisStream")
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class AnalysisStreamHealthIndicator implements HealthIndicator {

    private final StringRedisTemplate redis;

    public AnalysisStreamHealthIndicator(
            @Qualifier("analysisStreamRedisTemplate") StringRedisTemplate redis
    ) {
        this.redis = redis;
    }

    @Override
    public Health health() {
        try {
            String pong = redis.execute((RedisCallback<String>) connection -> connection.ping());
            return "PONG".equalsIgnoreCase(pong) ? Health.up().build() : Health.down().build();
        } catch (RuntimeException error) {
            return Health.down().build();
        }
    }
}
