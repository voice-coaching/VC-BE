package org.example.voice.analysis.infrastructure.stream;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAnalysisRequestPublisherTest {

    @Test
    void atomicallyIndexesOneRequestStreamIdPerEvent() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        UUID eventId = UUID.fromString("4adfe173-0691-4e89-b94e-a5c5c5085826");
        AnalysisStreamProperties properties = new AnalysisStreamProperties();
        when(redis.execute(
                any(RedisScript.class),
                eq(List.of(
                        properties.getRequestStream(),
                        properties.getRequestIndexKeyPrefix() + eventId
                )),
                eq("synthetic-payload")
        )).thenReturn("123-0");
        RedisAnalysisRequestPublisher publisher = new RedisAnalysisRequestPublisher(
                redis,
                properties,
                new AnalysisStreamMetrics(new SimpleMeterRegistry())
        );

        assertThat(publisher.publish(eventId, "synthetic-payload")).isEqualTo("123-0");

        verify(redis).execute(
                any(RedisScript.class),
                eq(List.of(
                        properties.getRequestStream(),
                        properties.getRequestIndexKeyPrefix() + eventId
                )),
                eq("synthetic-payload")
        );
    }
}
