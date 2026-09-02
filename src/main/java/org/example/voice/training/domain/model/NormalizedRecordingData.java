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
        BigDecimal noiseScore
) {
    public static final String CANONICAL_MIME_TYPE = "audio/wav";

    public NormalizedRecordingData {
        if (objectKey == null || objectKey.isBlank() || objectKey.length() > 1_000) {
            throw new IllegalArgumentException("normalized object key is invalid");
        }
        if (!CANONICAL_MIME_TYPE.equals(mimeType)) {
            throw new IllegalArgumentException("normalized media must be canonical WAV");
        }
        if (fileSizeBytes == null || fileSizeBytes <= 0) {
            throw new IllegalArgumentException("normalized file size is invalid");
        }
        if (durationMs == null || durationMs <= 0) {
            throw new IllegalArgumentException("normalized duration is invalid");
        }
        if (audioSha256 == null || !audioSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("normalized digest is invalid");
        }
        if (qualityStatus == null || qualityStatus == RecordingQualityStatus.PENDING) {
            throw new IllegalArgumentException("normalized quality result must be terminal");
        }
    }
}
