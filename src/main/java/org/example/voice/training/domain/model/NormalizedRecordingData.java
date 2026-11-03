package org.example.voice.training.domain.model;

import org.example.voice.training.domain.type.RecordingQualityStatus;

import java.math.BigDecimal;

/** Canonical audio object and measured technical-quality evidence produced by the backend. */
public record NormalizedRecordingData(
        String objectKey,
        String mimeType,
        Long fileSizeBytes,
        Integer durationMs,
        String audioSha256,
        RecordingQualityStatus qualityStatus,
        BigDecimal volumeScore,
        BigDecimal noiseScore,
        NormalizedVisualData visual,
        MediaPreparationData preparation
) {
    public static final String CANONICAL_MIME_TYPE = "audio/wav";

    public NormalizedRecordingData(String objectKey, String mimeType, Long fileSizeBytes,
            Integer durationMs, String audioSha256, RecordingQualityStatus qualityStatus,
            BigDecimal volumeScore, BigDecimal noiseScore, NormalizedVisualData visual) {
        this(objectKey, mimeType, fileSizeBytes, durationMs, audioSha256, qualityStatus,
                volumeScore, noiseScore, visual, null);
    }

    public NormalizedRecordingData(
            String objectKey,
            String mimeType,
            Long fileSizeBytes,
            Integer durationMs,
            String audioSha256,
            RecordingQualityStatus qualityStatus,
            BigDecimal volumeScore,
            BigDecimal noiseScore
    ) {
        this(objectKey, mimeType, fileSizeBytes, durationMs, audioSha256,
                qualityStatus, volumeScore, noiseScore, null);
    }

    public NormalizedRecordingData {

    }
}
