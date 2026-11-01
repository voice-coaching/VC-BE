package org.example.voice.practicecontent.controller.dto;

import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.domain.type.Difficulty;

public record PracticeContentNextConditionDto(
        @io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        ContentType type,
        String category,
        Difficulty difficulty,
        Long excludeId
) {
}
