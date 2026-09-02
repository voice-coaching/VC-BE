package org.example.voice.analysis.infrastructure.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.example.voice.analysis.application.AnalysisResultIngestionService;
import org.example.voice.analysis.domain.model.AnalysisWorkerResult;
import org.example.voice.analysis.domain.type.AnalysisResultIngestionDisposition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Reads AI results under a consumer group and ACKs only after the DB transaction
 * completes. Pending records are reclaimed after the configured idle timeout.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class RedisAnalysisResultConsumer {

    private static final String PAYLOAD_FIELD = "payload";
    private static final String DLQ_FAILURE_CODE = "analysis_result_retry_exhausted";
    private static final String INVALID_PAYLOAD_DLQ_FAILURE_CODE = "analysis_result_invalid_payload";
    private static final String MISSING_PAYLOAD_DLQ_FAILURE_CODE = "analysis_result_missing_payload";
    private static final String SOURCE_RECORD_MISSING_DLQ_FAILURE_CODE = "analysis_result_source_record_missing";
    private static final String UNKNOWN_ANALYSIS_DLQ_FAILURE_CODE = "analysis_result_unknown_analysis";
    private static final String OVERSIZED_PAYLOAD_DLQ_FAILURE_CODE = "analysis_result_payload_too_large";

    private final StringRedisTemplate stringRedisTemplate;
    private final AnalysisStreamProperties properties;
    private final AnalysisStreamCodec codec;
    private final AnalysisResultIngestionService ingestionService;
    private final AnalysisStreamMetrics metrics;

    private volatile boolean consumerGroupReady;

    public RedisAnalysisResultConsumer(
            @Qualifier("analysisStreamRedisTemplate") StringRedisTemplate stringRedisTemplate,
            AnalysisStreamProperties properties,
            AnalysisStreamCodec codec,
            AnalysisResultIngestionService ingestionService,
            AnalysisStreamMetrics metrics
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.codec = codec;
        this.ingestionService = ingestionService;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${analysis.stream.result-poll-interval:PT1S}")
    public void consume() {
        ensureConsumerGroup();
        reclaimPending();
        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                Consumer.from(properties.getResultConsumerGroup(), properties.getResultConsumerName()),
                StreamReadOptions.empty()
                        .count(properties.getBatchSize())
                        .block(properties.getResultBlock()),
                StreamOffset.create(properties.getResultStream(), ReadOffset.lastConsumed())
        );
        process(records == null ? List.of() : records);
    }

    private void ensureConsumerGroup() {
        if (consumerGroupReady) {
            return;
        }
        try {
            stringRedisTemplate.execute((RedisCallback<Object>) connection -> connection.execute(
                    "XGROUP",
                    bytes("CREATE"),
                    bytes(properties.getResultStream()),
                    bytes(properties.getResultConsumerGroup()),
                    bytes("0-0"),
                    bytes("MKSTREAM")
            ));
            consumerGroupReady = true;
        } catch (DataAccessException error) {
            if (hasBusyGroup(error)) {
                consumerGroupReady = true;
                return;
            }
            throw error;
        }
    }

    private void reclaimPending() {
        PendingMessages pending = stringRedisTemplate.opsForStream().pending(
                properties.getResultStream(),
                properties.getResultConsumerGroup(),
                Range.unbounded(),
                properties.getBatchSize(),
                properties.getPendingClaimIdle()
        );
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (PendingMessage message : pending) {
            if (message.getTotalDeliveryCount() >= properties.getMaxRetries()) {
                deadLetter(message.getId());
                continue;
            }
            List<MapRecord<String, Object, Object>> claimed = stringRedisTemplate.opsForStream().claim(
                    properties.getResultStream(),
                    properties.getResultConsumerGroup(),
                    properties.getResultConsumerName(),
                    properties.getPendingClaimIdle(),
                    message.getId()
            );
            process(claimed == null ? List.of() : claimed);
        }
    }

    private void process(List<MapRecord<String, Object, Object>> records) {
        for (MapRecord<String, Object, Object> record : records) {
            try {
                String payload = payloadOf(record);
                if (payload == null || payload.isBlank()) {
                    throw new IllegalArgumentException("stream record has no payload");
                }
                if (payloadBytes(payload) > properties.getMaximumPayloadBytes()) {
                    throw new IllegalArgumentException("analysis result payload exceeds configured maximum");
                }
                AnalysisResultIngestionDisposition disposition = ingestionService.ingest(codec.decodeResult(payload));
                metrics.resultIngested(disposition);
                stringRedisTemplate.opsForStream().acknowledge(
                        properties.getResultStream(),
                        properties.getResultConsumerGroup(),
                        record.getId()
                );
            } catch (RuntimeException error) {
                metrics.resultDeliveryFailed();
                log.warn("analysis result stream record was not acknowledged: streamRecordId={}", record.getId());
            }
        }
    }

    private void deadLetter(RecordId recordId) {
        List<MapRecord<String, Object, Object>> source = stringRedisTemplate.opsForStream().range(
                properties.getResultStream(),
                Range.closed(recordId.getValue(), recordId.getValue())
        );
        if (source == null || source.isEmpty()) {
            moveToDeadLetter(recordId, "", SOURCE_RECORD_MISSING_DLQ_FAILURE_CODE);
            acknowledge(recordId);
            return;
        }
        String payload = payloadOf(source.getFirst());
        if (payload == null || payload.isBlank()) {
            moveToDeadLetter(recordId, "", MISSING_PAYLOAD_DLQ_FAILURE_CODE);
            acknowledge(recordId);
            return;
        }
        if (payloadBytes(payload) > properties.getMaximumPayloadBytes()) {
            moveToDeadLetter(recordId, "", OVERSIZED_PAYLOAD_DLQ_FAILURE_CODE);
            acknowledge(recordId);
            return;
        }
        AnalysisWorkerResult decoded = tryDecode(payload);
        String failureCode = decoded == null ? INVALID_PAYLOAD_DLQ_FAILURE_CODE : DLQ_FAILURE_CODE;
        if (decoded != null) {
            try {
                ingestionService.failAfterDeliveryExhausted(decoded);
            } catch (IllegalArgumentException ignored) {
                failureCode = UNKNOWN_ANALYSIS_DLQ_FAILURE_CODE;
            }
        }
        moveToDeadLetter(recordId, payload, failureCode);
        acknowledge(recordId);
    }

    private void moveToDeadLetter(RecordId recordId, String payload, String failureCode) {
        stringRedisTemplate.opsForStream().add(
                StreamRecords.mapBacked(Map.of(
                        PAYLOAD_FIELD, payload,
                        "sourceStreamId", recordId.getValue(),
                        "failureCode", failureCode
                )).withStreamKey(properties.getResultDeadLetterStream()),
                RedisStreamCommands.XAddOptions
                        .maxlen(properties.getDeadLetterMaximumLength())
                        .approximateTrimming(true)
        );
        metrics.resultDeadLettered();
    }

    private void acknowledge(RecordId recordId) {
        stringRedisTemplate.opsForStream().acknowledge(
                properties.getResultStream(),
                properties.getResultConsumerGroup(),
                recordId
        );
    }

    private AnalysisWorkerResult tryDecode(String payload) {
        try {
            return codec.decodeResult(payload);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String payloadOf(MapRecord<String, Object, Object> record) {
        Object value = record.getValue().get(PAYLOAD_FIELD);
        return value instanceof String payload ? payload : null;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    static int payloadBytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean hasBusyGroup(DataAccessException error) {
        String message = error.getMessage();
        return message != null && message.contains("BUSYGROUP");
    }
}
