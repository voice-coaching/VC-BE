package org.example.voice.analysis.infrastructure.stream;

import org.example.voice.training.infrastructure.storage.ObjectStorageProperties;
import org.example.voice.training.infrastructure.storage.MediaNormalizationProperties;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisProductionConfigurationGuardTest {
    @Test
    void acceptsPrivateTlsStreamAndRealStorageConfiguration() {
        AnalysisStreamProperties stream = validStream();
        ObjectStorageProperties storage = new ObjectStorageProperties();
        storage.setEnabled(true);
        MediaNormalizationProperties media = new MediaNormalizationProperties();
        media.setEnabled(true);

        assertThatCode(() -> new AnalysisProductionConfigurationGuard(storage, media, stream))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesNonTlsOrPasswordlessAnalysisStream() {
        AnalysisStreamProperties stream = validStream();
        stream.setRedisSslEnabled(false);
        ObjectStorageProperties storage = new ObjectStorageProperties();
        storage.setEnabled(true);
        MediaNormalizationProperties media = new MediaNormalizationProperties();
        media.setEnabled(true);

        assertThatThrownBy(() -> new AnalysisProductionConfigurationGuard(storage, media, stream))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("analysis_stream_configuration_invalid");
    }

    @Test
    void refusesStreamAnalysisWithoutBackendMediaNormalization() {
        AnalysisStreamProperties stream = validStream();
        ObjectStorageProperties storage = new ObjectStorageProperties();
        storage.setEnabled(true);
        MediaNormalizationProperties media = new MediaNormalizationProperties();

        assertThatThrownBy(() -> new AnalysisProductionConfigurationGuard(storage, media, stream))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("analysis_media_normalization_must_be_enabled");
    }

    @Test
    void refusesAnInvalidCancellationTombstonePrefix() {
        AnalysisStreamProperties stream = validStream();
        stream.setCancellationKeyPrefix("invalid prefix:");
        ObjectStorageProperties storage = new ObjectStorageProperties();
        storage.setEnabled(true);
        MediaNormalizationProperties media = new MediaNormalizationProperties();
        media.setEnabled(true);

        assertThatThrownBy(() -> new AnalysisProductionConfigurationGuard(storage, media, stream))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("analysis_stream_configuration_invalid");
    }

    private static AnalysisStreamProperties validStream() {
        AnalysisStreamProperties stream = new AnalysisStreamProperties();
        stream.setRedisSslEnabled(true);
        stream.setRedisPassword("synthetic-test-password");
        return stream;
    }
}
