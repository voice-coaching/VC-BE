package org.example.voice.onboarding.controller.dto;

import org.example.voice.onboarding.domain.SurveyAnswers;

import java.util.List;

public record SurveyAnswersResponseDto(
        List<String> learningPurposes,
        List<String> improvementAreas,
        List<String> pronunciationConcerns,
        List<String> learningSituations
) {

    public static SurveyAnswersResponseDto from(SurveyAnswers surveyAnswers) {
        return new SurveyAnswersResponseDto(
                surveyAnswers.getLearningPurposes(),
                surveyAnswers.getImprovementAreas(),
                surveyAnswers.getPronunciationConcerns(),
                surveyAnswers.getLearningSituations()
        );
    }
}
