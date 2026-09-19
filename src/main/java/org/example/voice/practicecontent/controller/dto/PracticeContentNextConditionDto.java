package org.example.voice.practicecontent.controller.dto;

import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.domain.type.Difficulty;

public record PracticeContentNextConditionDto(
        ContentType type,
        String category,
        Difficulty difficulty,
        Long excludeId
) {
}
