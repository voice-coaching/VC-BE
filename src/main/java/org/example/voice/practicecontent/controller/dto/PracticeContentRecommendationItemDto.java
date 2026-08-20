package org.example.voice.practicecontent.controller.dto;

import org.example.voice.practicecontent.domain.model.PracticeContentRecommendationData;

public record PracticeContentRecommendationItemDto(
        Long id,
        String title,
        String contentType,
        String similarityReason
) {

    public static PracticeContentRecommendationItemDto from(PracticeContentRecommendationData data) {
        return new PracticeContentRecommendationItemDto(
                data.id(),
                data.title(),
                data.contentType().name(),
                data.similarityReason()
        );
    }
}
