package org.example.voice.analysis.infrastructure.stream;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AnalysisStreamResourceLimitsTest {

    @Test
    void countsPayloadLimitInUtf8Bytes() {
        assertThat(RedisAnalysisResultConsumer.payloadBytes("가")).isEqualTo(3);
    }

    @Test
    void rejectsAnOversizedRequestBeforeRedisIo() {
        AnalysisStreamProperties properties = new AnalysisStreamProperties();
        properties.setMaximumPayloadBytes(2);
        RedisAnalysisRequestPublisher publisher = new RedisAnalysisRequestPublisher(
                mock(StringRedisTemplate.class),
                properties,
                new AnalysisStreamMetrics(new SimpleMeterRegistry())
        );

        assertThatThrownBy(() -> publisher.publish("가"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("analysis_request_payload_size_invalid");
    }
}
