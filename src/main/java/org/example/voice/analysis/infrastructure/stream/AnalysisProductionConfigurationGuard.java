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
        {}
        {}
        {}
        {}
        {}
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean invalidDuration(java.time.Duration value) {
        return value == null || value.isZero() || value.isNegative();
    }

    private static boolean validCancellationPrefix(String value) {
        return value != null
                && value.length() <= 100
                && value.endsWith(":")
                && value.matches("[A-Za-z0-9:_-]+");
    }
}
