package org.example.voice.practicecontent.controller.dto;

import org.example.voice.practicecontent.domain.model.ReferenceAudioData;

import java.util.List;

public record ReferenceAudioResponseDto(
        List<ReferenceAudioItemDto> items
) {

    public static ReferenceAudioResponseDto from(List<ReferenceAudioData> items) {
        return new ReferenceAudioResponseDto(
                items.stream()
                        .map(ReferenceAudioItemDto::from)
                        .toList()
        );
    }
}
