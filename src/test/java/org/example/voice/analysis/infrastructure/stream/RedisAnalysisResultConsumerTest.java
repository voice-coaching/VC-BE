package org.example.voice.analysis.infrastructure.stream;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.voice.analysis.application.AnalysisResultIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisAnalysisResultConsumerTest {

    @Test
    void acknowledgesAndDeletesAResultInOneRedisOperation() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doReturn(1L).when(redis).execute(
                any(RedisScript.class),
                eq(List.of("analysis:result:v1")),
                eq("backend-analysis-result-workers"),
                eq("123-0")
        );
        RedisAnalysisResultConsumer consumer = consumer(redis);

        consumer.acknowledge(RecordId.of("123-0"));

        verify(redis).execute(
                any(RedisScript.class),
                eq(List.of("analysis:result:v1")),
                eq("backend-analysis-result-workers"),
                eq("123-0")
        );
    }

    @Test
    void refusesToTreatAnUnconfirmedAcknowledgementAsCleanup() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisAnalysisResultConsumer consumer = consumer(redis);

        assertThatThrownBy(() -> consumer.acknowledge(RecordId.of("123-0")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("analysis_result_acknowledgement_unconfirmed");
    }

    private static RedisAnalysisResultConsumer consumer(StringRedisTemplate redis) {
        return new RedisAnalysisResultConsumer(
                redis,
                new AnalysisStreamProperties(),
                mock(AnalysisStreamCodec.class),
                mock(AnalysisResultIngestionService.class),
                new AnalysisStreamMetrics(new SimpleMeterRegistry())
        );
    }
}
