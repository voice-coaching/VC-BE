package org.example.voice.analysis.infrastructure.stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Writes an opaque tombstone shared by every AI worker until safe retention cleanup. */
@Component
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class RedisAnalysisCancellationPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final AnalysisStreamProperties properties;
    private final AnalysisStreamMetrics metrics;

    public RedisAnalysisCancellationPublisher(
            @Qualifier("analysisStreamRedisTemplate") StringRedisTemplate stringRedisTemplate,
            AnalysisStreamProperties properties,
            AnalysisStreamMetrics metrics
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    public void publish(UUID requestEventId) {
        String key = properties.getCancellationKeyPrefix() + requestEventId;
        stringRedisTemplate.opsForValue().set(key, "1");
        metrics.cancellationPublished();
    }
}
