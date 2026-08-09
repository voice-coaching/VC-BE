package org.example.voice.training.controller.dto;

import org.example.voice.training.domain.model.VoiceRecordingData;

import java.util.List;

public record VoiceRecordingListResponseDto(
        List<VoiceRecordingItemDto> items
) {

    public static VoiceRecordingListResponseDto from(List<VoiceRecordingData> items) {
        return new VoiceRecordingListResponseDto(
                items.stream()
                        .map(VoiceRecordingItemDto::from)
                        .toList()
        );
    }
}
