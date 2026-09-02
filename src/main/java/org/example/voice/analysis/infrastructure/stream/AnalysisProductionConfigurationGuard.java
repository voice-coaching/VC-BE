package org.example.voice.analysis.infrastructure.stream;

import org.example.voice.training.infrastructure.storage.ObjectStorageProperties;
import org.example.voice.training.infrastructure.storage.MediaNormalizationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class AnalysisProductionConfigurationGuard {
    public AnalysisProductionConfigurationGuard(
            ObjectStorageProperties storage,
            MediaNormalizationProperties mediaNormalization,
            AnalysisStreamProperties stream
    ) {
        if (!storage.isEnabled()) {
            throw new IllegalStateException("analysis_object_storage_must_be_enabled");
        }
        if (!mediaNormalization.isEnabled()) {
            throw new IllegalStateException("analysis_media_normalization_must_be_enabled");
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
        if (invalidDuration(stream.getResultBlock())
                || invalidDuration(stream.getRedisConnectTimeout())
                || invalidDuration(stream.getRedisCommandTimeout())
                || invalidDuration(stream.getRedisShutdownTimeout())
                || stream.getRedisCommandTimeout().compareTo(stream.getResultBlock()) <= 0
                || stream.getMaximumPayloadBytes() <= 0
                || stream.getMaximumPayloadBytes() > 1_048_576
                || stream.getDeadLetterMaximumLength() <= 0
                || stream.getDeadLetterMaximumLength() > 1_000_000) {
            throw new IllegalStateException("analysis_stream_resource_limits_invalid");
        }
        if (stream.getMaxConcurrentPerUser() <= 0
                || invalidDuration(stream.getExecutionTimeout())
                || invalidDuration(stream.getTimeoutSweepInterval())
                || stream.getTimeoutSweepBatchSize() <= 0) {
            throw new IllegalStateException("analysis_execution_configuration_invalid");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean invalidDuration(java.time.Duration value) {
        return value == null || value.isZero() || value.isNegative();
    }
}
