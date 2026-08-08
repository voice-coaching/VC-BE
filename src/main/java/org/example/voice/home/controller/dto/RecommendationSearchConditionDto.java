package org.example.voice.home.controller.dto;

import org.example.voice.practicecontent.domain.type.ContentType;

public record RecommendationSearchConditionDto(
        ContentType type,
        Integer limit
) {
}
