package org.example.voice.home.controller.dto;

import org.example.voice.home.domain.model.RecommendationItemData;

public record HomeRecommendationItemDto(
        Long contentId,
        String title,
        String contentType,
        String reason
) {

    public static HomeRecommendationItemDto from(RecommendationItemData data) {
        return new HomeRecommendationItemDto(
                data.contentId(),
                data.title(),
                data.contentType().name(),
                data.reason()
        );
    }
}
