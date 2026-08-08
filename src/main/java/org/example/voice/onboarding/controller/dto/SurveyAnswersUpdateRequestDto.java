package org.example.voice.onboarding.controller.dto;

import java.util.List;

public record SurveyAnswersUpdateRequestDto(
        List<String> learningPurposes,
        List<String> improvementAreas,
        List<String> pronunciationConcerns,
        List<String> learningSituations
) {
}
