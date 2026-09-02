package org.example.voice.analysis.infrastructure.stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-before-delete Redis boundary used only by DB-gated retention cleanup. */
@Component
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class RedisAnalysisRetentionStore {

    private final StringRedisTemplate redis;
    private final AnalysisStreamProperties properties;

    public RedisAnalysisRetentionStore(
            @Qualifier("analysisStreamRedisTemplate") StringRedisTemplate redis,
            AnalysisStreamProperties properties
    ) {
        this.redis = redis;
        this.properties = properties;
    }

    public Optional<String> indexedStreamId(UUID eventId) {
        String value = redis.opsForValue().get(indexKey(eventId));
        if (value == null) {
            return Optional.empty();
        }
        requireStreamId(value);
        return Optional.of(value);
    }

    public boolean requestEntryExists(String streamId) {
        requireStreamId(streamId);
        var records = redis.opsForStream().range(
                properties.getRequestStream(),
                Range.closed(streamId, streamId)
        );
        return records != null && !records.isEmpty();
    }

    public void deleteMarkers(UUID eventId) {
        redis.delete(List.of(
                indexKey(eventId),
                properties.getCancellationKeyPrefix() + eventId
        ));
    }

    private String indexKey(UUID eventId) {
        return properties.getRequestIndexKeyPrefix() + eventId;
    }

    private static void requireStreamId(String value) {
        if (!value.matches("[0-9]+-[0-9]+") || value.length() > 64) {
            throw new IllegalStateException("analysis_request_index_invalid");
        }
    }
}
