package org.example.voice.practicecontent.domain.model;

import org.example.voice.practicecontent.domain.type.SpeakerType;

public record ReferenceAudioData(
        Long id,
        String speakerName,
        SpeakerType speakerType,
        Integer durationMs,
        Boolean primary
) {
}
