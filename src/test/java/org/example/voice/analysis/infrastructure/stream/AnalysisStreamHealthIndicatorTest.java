package org.example.voice.analysis.infrastructure.stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisStreamHealthIndicatorTest {

    @Test
    void reportsUpOnlyAfterDedicatedRedisPing() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisCallback.class))).thenReturn("PONG");

        assertThat(new AnalysisStreamHealthIndicator(redis).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWithoutLeakingConnectionFailureDetails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisCallback.class)))
                .thenThrow(new RedisConnectionFailureException("sensitive-host-detail"));

        var health = new AnalysisStreamHealthIndicator(redis).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).isEmpty();
    }
}
