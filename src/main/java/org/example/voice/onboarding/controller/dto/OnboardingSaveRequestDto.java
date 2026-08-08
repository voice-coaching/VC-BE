package org.example.voice.onboarding.controller.dto;

import org.example.voice.onboarding.domain.CurrentLevel;

public record OnboardingSaveRequestDto(
        CurrentLevel currentLevel,
        String goalText,
        Integer dailyGoalMinutes,
        Integer weeklyGoalCount,
        SurveyAnswersRequestDto surveyAnswers
) {
}
