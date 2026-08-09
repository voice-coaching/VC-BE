package org.example.voice.training.domain.model;

import org.example.voice.training.domain.type.RecordingQualityStatus;

import java.time.OffsetDateTime;

public record VoiceRecordingRegisteredData(
        Long recordingId,
        Integer attemptNo,
        RecordingQualityStatus qualityStatus,
        Boolean selected,
        OffsetDateTime createdAt
) {
}
