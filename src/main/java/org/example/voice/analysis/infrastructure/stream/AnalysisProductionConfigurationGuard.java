package org.example.voice.analysis.infrastructure.stream;

import org.example.voice.training.infrastructure.storage.ObjectStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class AnalysisProductionConfigurationGuard {
    public AnalysisProductionConfigurationGuard(
            ObjectStorageProperties storage,
            AnalysisStreamProperties stream
    ) {
        if (!storage.isEnabled()) {
            throw new IllegalStateException("analysis_object_storage_must_be_enabled");
        }
        if (!stream.isRedisSslEnabled()
                || !hasText(stream.getRedisHost())
                || stream.getRedisPort() < 1
                || stream.getRedisPort() > 65_535
                || !hasText(stream.getRedisPassword())
                || !hasText(stream.getRequestStream())
                || !hasText(stream.getResultStream())
                || !hasText(stream.getResultConsumerGroup())
                || !hasText(stream.getResultConsumerName())
                || !hasText(stream.getResultDeadLetterStream())
                || stream.getBatchSize() <= 0
                || stream.getMaxRetries() <= 0
                || stream.getPendingClaimIdle() == null
                || stream.getPendingClaimIdle().isZero()
                || stream.getPendingClaimIdle().isNegative()) {
            throw new IllegalStateException("analysis_stream_configuration_invalid");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
