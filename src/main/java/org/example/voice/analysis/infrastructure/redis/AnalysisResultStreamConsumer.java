package org.example.voice.analysis.infrastructure.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice.analysis.application.AnalysisResultStreamService;
import org.example.voice.analysis.domain.model.AnalysisResultStreamData;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.domain.type.SegmentMatchType;
import org.example.voice.analysis.domain.type.SegmentResultStatus;
import org.example.voice.analysis.domain.type.SpeedStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis.redis-stream", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AnalysisResultStreamConsumer {

    private static final TypeReference<List<AnalysisResultStreamMessageDto.SegmentDto>> SEGMENT_LIST_TYPE =
            new TypeReference<>() {
            };

    private final StringRedisTemplate redisTemplate;
    private final AnalysisResultStreamService analysisResultStreamService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${analysis.redis-stream.result-stream:analysis:result}")
    private String resultStream;

    @Value("${analysis.redis-stream.backend-consumer-group:backend-analysis-result-workers}")
    private String consumerGroup;

    @Value("${analysis.redis-stream.backend-consumer-name:voice-backend}")
    private String consumerName;

    @Value("${analysis.redis-stream.poll-count:10}")
    private int pollCount;

    @Value("${analysis.redis-stream.pending-timeout-millis:300000}")
    private long pendingTimeoutMillis;

    @Scheduled(fixedDelayString = "${analysis.redis-stream.poll-delay-millis:1000}")
    @SuppressWarnings("unchecked")
    public void consume() {
        ensureConsumerGroup();
        processPendingMessages();
        List<MapRecord<String, Object, Object>> records;
        try {
            records = redisTemplate.opsForStream().read(
                    Consumer.from(consumerGroup, consumerName),
                    org.springframework.data.redis.connection.stream.StreamReadOptions.empty()
                            .count(pollCount)
                            .block(Duration.ofSeconds(1)),
                    StreamOffset.create(resultStream, ReadOffset.lastConsumed())
            );
        } catch (DataAccessException exception) {
            log.debug("Analysis result stream read skipped. stream={}, group={}",
                    resultStream, consumerGroup, exception);
            return;
        }
        if (records == null || records.isEmpty()) {
            return;
        }

        for (MapRecord<String, Object, Object> record : records) {
            process(record);
        }
    }

    private void processPendingMessages() {
        try {
            PendingMessages pendingMessages = redisTemplate.opsForStream().pending(
                    resultStream,
                    consumerGroup,
                    Range.unbounded(),
                    pollCount,
                    Duration.ofMillis(pendingTimeoutMillis)
            );
            if (pendingMessages == null || pendingMessages.isEmpty()) {
                return;
            }

            RecordId[] recordIds = pendingMessages.stream()
                    .map(PendingMessage::getId)
                    .toArray(RecordId[]::new);
            List<MapRecord<String, Object, Object>> claimedRecords = redisTemplate.opsForStream().claim(
                    resultStream,
                    consumerGroup,
                    consumerName,
                    Duration.ofMillis(pendingTimeoutMillis),
                    recordIds
            );
            if (claimedRecords == null || claimedRecords.isEmpty()) {
                return;
            }
            for (MapRecord<String, Object, Object> record : claimedRecords) {
                process(record);
            }
        } catch (DataAccessException exception) {
            log.debug("Analysis result stream pending claim skipped. stream={}, group={}",
                    resultStream, consumerGroup, exception);
        }
    }

    private void process(MapRecord<String, Object, Object> record) {
        try {
            analysisResultStreamService.applyResult(toData(record.getValue()));
            redisTemplate.opsForStream().acknowledge(resultStream, consumerGroup, record.getId());
        } catch (Exception exception) {
            log.warn("Failed to process analysis result stream message. stream={}, recordId={}",
                    resultStream, record.getId(), exception);
        }
    }

    private void ensureConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(resultStream, ReadOffset.from("0-0"), consumerGroup);
        } catch (DataAccessException exception) {
            if (exception.getMessage() == null || !exception.getMessage().contains("BUSYGROUP")) {
                log.debug("Analysis result stream consumer group is not ready yet. stream={}, group={}",
                        resultStream, consumerGroup, exception);
            }
        }
    }

    private AnalysisResultStreamData toData(Map<Object, Object> fields) throws Exception {
        AnalysisResultStreamMessageDto message = new AnalysisResultStreamMessageDto(
                longValue(fields, "analysisId"),
                text(fields, "status"),
                text(fields, "transcript"),
                decimalValue(fields, "sttConfidence"),
                text(fields, "sttModelName"),
                decimalValue(fields, "overallScore"),
                decimalValue(fields, "pronunciationScore"),
                decimalValue(fields, "intonationScore"),
                decimalValue(fields, "speedWpm"),
                text(fields, "speedStatus"),
                decimalValue(fields, "stressScore"),
                decimalValue(fields, "pauseScore"),
                text(fields, "strengthsText"),
                text(fields, "weaknessesText"),
                text(fields, "summaryFeedback"),
                text(fields, "failureReason"),
                segments(fields)
        );

        return new AnalysisResultStreamData(
                message.analysisId(),
                enumValue(AnalysisStatus.class, message.status()),
                message.transcript(),
                message.sttConfidence(),
                message.sttModelName(),
                message.overallScore(),
                message.pronunciationScore(),
                message.intonationScore(),
                message.speedWpm(),
                enumValue(SpeedStatus.class, message.speedStatus()),
                message.stressScore(),
                message.pauseScore(),
                message.strengthsText(),
                message.weaknessesText(),
                message.summaryFeedback(),
                message.failureReason(),
                message.segments().stream()
                        .map(this::toSegmentData)
                        .toList()
        );
    }

    private AnalysisResultStreamData.Segment toSegmentData(AnalysisResultStreamMessageDto.SegmentDto segment) {
        return new AnalysisResultStreamData.Segment(
                segment.sequenceNo(),
                segment.expectedText(),
                segment.recognizedText(),
                segment.startMs(),
                segment.endMs(),
                enumValue(SegmentMatchType.class, segment.matchType()),
                enumValue(SegmentResultStatus.class, segment.resultStatus()),
                segment.targetUnit(),
                segment.errorType(),
                segment.pronunciationScore(),
                segment.intonationScore(),
                segment.feedback()
        );
    }

    private List<AnalysisResultStreamMessageDto.SegmentDto> segments(Map<Object, Object> fields) throws Exception {
        String rawSegments = text(fields, "segments");
        if (rawSegments == null || rawSegments.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(rawSegments, SEGMENT_LIST_TYPE);
    }

    private String text(Map<Object, Object> fields, String key) {
        Object value = fields.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.equalsIgnoreCase("null") ? null : text;
    }

    private Long longValue(Map<Object, Object> fields, String key) {
        String value = text(fields, key);
        return value == null ? null : Long.valueOf(value);
    }

    private BigDecimal decimalValue(Map<Object, Object> fields, String key) {
        String value = text(fields, key);
        return value == null ? null : new BigDecimal(value);
    }

    private <T extends Enum<T>> T enumValue(Class<T> enumType, String value) {
        return value == null ? null : Enum.valueOf(enumType, value);
    }
}
