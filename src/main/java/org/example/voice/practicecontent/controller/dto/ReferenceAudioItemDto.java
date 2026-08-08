package org.example.voice.practicecontent.controller.dto;

import org.example.voice.practicecontent.domain.model.ReferenceAudioData;

public record ReferenceAudioItemDto(
        Long id,
        String speakerName,
        String speakerType,
        Integer durationMs,
        Boolean primary
) {

    public static ReferenceAudioItemDto from(ReferenceAudioData data) {
        return new ReferenceAudioItemDto(
                data.id(),
                data.speakerName(),
                data.speakerType() == null ? null : data.speakerType().name(),
                data.durationMs(),
                data.primary()
        );
    }
}
