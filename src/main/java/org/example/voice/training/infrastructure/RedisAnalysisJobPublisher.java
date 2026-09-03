package org.example.voice.training.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.training.domain.model.AnalysisJobRequestData;
import org.example.voice.training.domain.port.AnalysisJobPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis.redis-stream", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisAnalysisJobPublisher implements AnalysisJobPublisher {

    private final StringRedisTemplate redisTemplate;

    @Value("${analysis.redis-stream.request-stream:analysis:request}")
    private String requestStream;

    @Override
    public void publish(AnalysisJobRequestData request) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("analysisId", request.analysisId().toString());
        fields.put("sessionId", request.sessionId().toString());
        fields.put("recordingId", request.recordingId().toString());
        fields.put("userId", request.userId().toString());
        fields.put("audioUrl", request.audioUrl());
        fields.put("scriptText", request.scriptText());
        fields.put("learningFocus", request.learningFocus().name());

        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .ofMap(fields)
                .withStreamKey(requestStream);
        redisTemplate.opsForStream().add(record);
    }
}
