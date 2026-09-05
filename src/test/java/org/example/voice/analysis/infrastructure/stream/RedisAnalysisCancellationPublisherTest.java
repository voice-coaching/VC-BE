package org.example.voice.analysis.infrastructure.stream;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAnalysisCancellationPublisherTest {

    @Test
    void writesAnOpaqueDurableCancellationKey() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        AnalysisStreamProperties properties = new AnalysisStreamProperties();
        properties.setCancellationKeyPrefix("analysis:canceled:v1:");
        RedisAnalysisCancellationPublisher publisher = new RedisAnalysisCancellationPublisher(
                redis,
                properties,
                new AnalysisStreamMetrics(new SimpleMeterRegistry())
        );
        UUID eventId = UUID.fromString("4adfe173-0691-4e89-b94e-a5c5c5085826");

        publisher.publish(eventId);

        verify(values).set(
                "analysis:canceled:v1:4adfe173-0691-4e89-b94e-a5c5c5085826",
                "1"
        );
    }
}
