package org.example.voice.training.infrastructure.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "storage.media-normalization")
public class MediaNormalizationProperties {
    private boolean enabled;
    private Path workspaceRoot = Path.of("/tmp/voice-coach-media-normalization");
    private String ffmpegBinary = "/usr/bin/ffmpeg";
    private String ffprobeBinary = "/usr/bin/ffprobe";
    private Duration processTimeout = Duration.ofSeconds(30);
    private long maximumInputBytes = 100L * 1024L * 1024L;
    private long maximumNormalizedBytes = 20L * 1024L * 1024L;
    private int minimumDurationMs = 500;
    private int maximumDurationMs = 180_000;
}
