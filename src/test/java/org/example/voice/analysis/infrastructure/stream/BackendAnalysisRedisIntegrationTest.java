package org.example.voice.analysis.infrastructure.stream;

import org.example.voice.analysis.application.AnalysisResultIngestionService;
import org.example.voice.analysis.domain.model.AnalysisWorkerResult;
import org.example.voice.analysis.domain.type.AnalysisOutcome;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@EnabledIfEnvironmentVariable(named = "VC_BE_TEST_REDIS_HOST", matches = ".+")
class BackendAnalysisRedisIntegrationTest {

    @Test
    void publishesRequestAndAcknowledgesResultOnlyAfterIngestion() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        AnalysisStreamProperties properties = properties(suffix);
        AnalysisStreamRedisConfiguration configuration = new AnalysisStreamRedisConfiguration(properties);
        LettuceConnectionFactory connectionFactory = (LettuceConnectionFactory)
                configuration.analysisStreamConnectionFactory();
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate redis = configuration.analysisStreamRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        AnalysisStreamMetrics metrics = new AnalysisStreamMetrics(new SimpleMeterRegistry());
        UUID requestEventId = UUID.randomUUID();
        try {
            new RedisAnalysisRequestPublisher(redis, properties, metrics).publish(
                    requestEventId,
                    "{\"synthetic\":true}"
            );
            List<MapRecord<String, Object, Object>> requests = redis.opsForStream().range(
                    properties.getRequestStream(),
                    org.springframework.data.domain.Range.unbounded()
            );
            assertThat(requests).hasSize(1);
            assertThat(requests.getFirst().getValue()).containsEntry("payload", "{\"synthetic\":true}");

            AnalysisResultIngestionService ingestion = mock(AnalysisResultIngestionService.class);
            AnalysisStreamCodec codec = new AnalysisStreamCodec();
            AnalysisWorkerResult result = completedResult();
            String resultPayload = new com.fasterxml.jackson.databind.ObjectMapper()
                    .findAndRegisterModules()
                    .writeValueAsString(result);
            redis.opsForStream().add(
                    org.springframework.data.redis.connection.stream.StreamRecords
                            .mapBacked(Map.of("payload", resultPayload))
                            .withStreamKey(properties.getResultStream())
            );

            RedisAnalysisResultConsumer consumer =
                    new RedisAnalysisResultConsumer(redis, properties, codec, ingestion, metrics);
            consumer.consume();

            verify(ingestion).ingest(any(AnalysisWorkerResult.class));
            assertThat(redis.opsForStream().pending(
                    properties.getResultStream(), properties.getResultConsumerGroup()
            ).getTotalPendingMessages()).isZero();
            assertThat(redis.opsForStream().size(properties.getResultStream())).isZero();

            properties.setMaximumPayloadBytes(128);
            properties.setMaximumResultPayloadBytes(128);
            properties.setMaxRetries(1);
            redis.opsForStream().add(
                    org.springframework.data.redis.connection.stream.StreamRecords
                            .mapBacked(Map.of("payload", "x".repeat(129)))
                            .withStreamKey(properties.getResultStream())
            );
            consumer.consume();
            Thread.sleep(20);
            consumer.consume();

            List<MapRecord<String, Object, Object>> deadLetters = redis.opsForStream().range(
                    properties.getResultDeadLetterStream(),
                    org.springframework.data.domain.Range.unbounded()
            );
            assertThat(deadLetters).hasSize(1);
            assertThat(deadLetters.getFirst().getValue())
                    .containsEntry("payload", "")
                    .containsEntry("failureCode", "analysis_result_payload_too_large");
            assertThat(redis.opsForStream().pending(
                    properties.getResultStream(), properties.getResultConsumerGroup()
            ).getTotalPendingMessages()).isZero();
            assertThat(redis.opsForStream().size(properties.getResultStream())).isZero();
        } finally {
            redis.delete(List.of(
                    properties.getRequestStream(),
                    properties.getResultStream(),
                    properties.getResultDeadLetterStream(),
                    properties.getRequestIndexKeyPrefix() + requestEventId
            ));
            connectionFactory.destroy();
        }
    }

    private static AnalysisStreamProperties properties(String suffix) {
        AnalysisStreamProperties properties = new AnalysisStreamProperties();
        properties.setEnabled(true);
        properties.setRedisHost(System.getenv("VC_BE_TEST_REDIS_HOST"));
        properties.setRedisPort(Integer.parseInt(System.getenv("VC_BE_TEST_REDIS_PORT")));
        properties.setRedisPassword(System.getenv("VC_BE_TEST_REDIS_PASSWORD"));
        properties.setRedisSslEnabled(true);
        properties.setRequestStream("test:backend:analysis:request:" + suffix);
        properties.setResultStream("test:backend:analysis:result:" + suffix);
        properties.setResultDeadLetterStream("test:backend:analysis:result:dlq:" + suffix);
        properties.setResultConsumerGroup("test-backend-results-" + suffix);
        properties.setResultConsumerName("backend-1");
        properties.setResultBlock(Duration.ofMillis(10));
        properties.setPendingClaimIdle(Duration.ofMillis(10));
        properties.setBatchSize(5);
        return properties;
    }

    private static AnalysisWorkerResult completedResult() {
        return new AnalysisWorkerResult(
                AnalysisWorkerResult.SCHEMA_VERSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                35L,
                AnalysisStatus.COMPLETED,
                AnalysisOutcome.COMPLETED_NO_ISSUE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "synthetic-worker-v1",
                "synthetic-seungun-v1",
                "a".repeat(64),
                List.of()
        );
    }
}
