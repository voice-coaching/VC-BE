package org.example.voice.training.controller.dto;

import org.example.voice.training.domain.model.VoiceRecordingRegisteredData;

import java.time.OffsetDateTime;

public record VoiceRecordingResponseDto(
        Long recordingId,
        Integer attemptNo,
        String analysisMediaType,
        String qualityStatus,
        Boolean selected,
        OffsetDateTime createdAt
) {

    public static VoiceRecordingResponseDto from(VoiceRecordingRegisteredData data) {
        return new VoiceRecordingResponseDto(
                data.recordingId(),
                data.attemptNo(),
                data.analysisMediaType(),
                data.qualityStatus().name(),
                data.selected(),
                data.createdAt()
        );
    }
}
