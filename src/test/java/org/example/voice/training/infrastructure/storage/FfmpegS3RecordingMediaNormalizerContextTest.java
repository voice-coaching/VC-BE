package org.example.voice.training.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FfmpegS3RecordingMediaNormalizerContextTest {
    @TempDir Path temporaryDirectory;

    @Test
    void injectsSpringBootJacksonObjectMapperWhenMediaNormalizationIsEnabled() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withPropertyValues("storage.media-normalization.enabled=true")
                .withBean(ObjectStorageProperties.class)
                .withBean(MediaNormalizationProperties.class, this::mediaProperties)
                .withBean(S3Client.class, () -> mock(S3Client.class))
                .withBean(FfmpegS3RecordingMediaNormalizer.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ObjectMapper.class);
                    assertThat(context).hasSingleBean(FfmpegS3RecordingMediaNormalizer.class);
                });
    }

    private MediaNormalizationProperties mediaProperties() {
        String executable = Path.of(
                System.getProperty("java.home"),
                "bin",
                isWindows() ? "java.exe" : "java"
        ).toAbsolutePath().toString();
        MediaNormalizationProperties properties = new MediaNormalizationProperties();
        properties.setEnabled(true);
        properties.setWorkspaceRoot(temporaryDirectory.toAbsolutePath());
        properties.setSandboxPythonBinary(executable);
        properties.setFfmpegBinary(executable);
        properties.setFfprobeBinary(executable);
        return properties;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
