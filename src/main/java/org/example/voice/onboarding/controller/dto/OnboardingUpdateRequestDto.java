package org.example.voice.onboarding.controller.dto;

public record OnboardingUpdateRequestDto(
        String goalText,
        Integer dailyGoalMinutes,
        Integer weeklyGoalCount,
        SurveyAnswersUpdateRequestDto surveyAnswers
) {
}
