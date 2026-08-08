package org.example.voice.onboarding.controller.dto;

import java.util.List;

public record SurveyAnswersRequestDto(
        List<String> learningPurposes,
        List<String> improvementAreas,
        List<String> pronunciationConcerns,
        List<String> learningSituations
) {
}
