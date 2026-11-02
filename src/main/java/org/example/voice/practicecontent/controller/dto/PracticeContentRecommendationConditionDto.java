package org.example.voice.practicecontent.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

public record PracticeContentRecommendationConditionDto(
         @Schema(description = "추천 개수", defaultValue = "5", minimum = "1") Integer limit
) {
    public PracticeContentRecommendationConditionDto {
        if (limit == null) limit = 5;
    }
}
