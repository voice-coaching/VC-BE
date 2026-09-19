package org.example.voice.practicecontent.controller.dto;

import org.example.voice.practicecontent.domain.model.PracticeContentRecommendationData;

import java.util.List;

public record PracticeContentRecommendationsResponseDto(
        List<PracticeContentRecommendationItemDto> items
) {

    public static PracticeContentRecommendationsResponseDto from(List<PracticeContentRecommendationData> items) {
        return new PracticeContentRecommendationsResponseDto(
                items.stream()
                        .map(PracticeContentRecommendationItemDto::from)
                        .toList()
        );
    }
}
