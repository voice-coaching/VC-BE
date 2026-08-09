package org.example.voice.training.controller.dto;

import org.example.voice.training.domain.model.VoiceRecordingData;

public record VoiceRecordingItemDto(
        Long id,
        Integer attemptNo,
        Integer durationMs,
        String qualityStatus,
        Boolean selected
) {

    public static VoiceRecordingItemDto from(VoiceRecordingData data) {
        return new VoiceRecordingItemDto(
                data.id(),
                data.attemptNo(),
                data.durationMs(),
                data.qualityStatus().name(),
                data.selected()
        );
    }
}
