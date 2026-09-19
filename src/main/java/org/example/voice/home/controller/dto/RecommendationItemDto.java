package org.example.voice.home.controller.dto;

import org.example.voice.home.domain.model.RecommendationItemData;

public record RecommendationItemDto(
        Long contentId,
        String contentType,
        String title,
        String difficulty,
        String reason
) {

    public static RecommendationItemDto from(RecommendationItemData data) {
        return new RecommendationItemDto(
                data.contentId(),
                data.contentType().name(),
                data.title(),
                data.difficulty().name(),
                data.reason()
        );
    }
}
