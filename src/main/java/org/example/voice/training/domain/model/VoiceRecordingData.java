package org.example.voice.training.domain.model;

import org.example.voice.training.domain.type.RecordingQualityStatus;

public record VoiceRecordingData(
        Long id,
        Integer attemptNo,
        Integer durationMs,
        RecordingQualityStatus qualityStatus,
        Boolean selected
) {
}
