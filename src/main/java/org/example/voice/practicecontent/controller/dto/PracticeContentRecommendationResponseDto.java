package org.example.voice.practicecontent.controller.dto;

import org.example.voice.practicecontent.domain.model.PracticeContentSummaryData;

public record PracticeContentRecommendationResponseDto(
        Long id,
        String contentType,
        String title,
        String difficulty,
        String scriptText
) {

    public static PracticeContentRecommendationResponseDto from(PracticeContentSummaryData data) {
        return new PracticeContentRecommendationResponseDto(
                data.id(),
                data.contentType().name(),
                data.title(),
                data.difficulty().name(),
                data.scriptText()
        );
    }
}
