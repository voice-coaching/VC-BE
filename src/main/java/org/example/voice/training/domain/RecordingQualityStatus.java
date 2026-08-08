package org.example.voice.training.domain;

public enum RecordingQualityStatus {
    PENDING,
    PASS,
    LOW_VOLUME,
    TOO_NOISY,
    TOO_SHORT,
    NO_SPEECH,
    FAILED
}
