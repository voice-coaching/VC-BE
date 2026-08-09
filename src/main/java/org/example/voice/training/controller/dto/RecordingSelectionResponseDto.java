package org.example.voice.training.controller.dto;

import org.example.voice.training.domain.model.RecordingSelectionData;

import java.time.OffsetDateTime;

public record RecordingSelectionResponseDto(
        Long sessionId,
        Long selectedRecordingId,
        OffsetDateTime selectedAt
) {

    public static RecordingSelectionResponseDto from(RecordingSelectionData data) {
        return new RecordingSelectionResponseDto(data.sessionId(), data.selectedRecordingId(), data.selectedAt());
    }
}
