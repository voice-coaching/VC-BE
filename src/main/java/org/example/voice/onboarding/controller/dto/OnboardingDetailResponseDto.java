package org.example.voice.onboarding.controller.dto;

import org.example.voice.onboarding.domain.CurrentLevel;
import org.example.voice.onboarding.domain.OnboardingProfile;

import java.time.OffsetDateTime;

public record OnboardingDetailResponseDto(
        CurrentLevel currentLevel,
        String goalText,
        Integer dailyGoalMinutes,
        Integer weeklyGoalCount,
        SurveyAnswersResponseDto surveyAnswers,
        OffsetDateTime completedAt
) {

    public static OnboardingDetailResponseDto from(OnboardingProfile onboardingProfile) {
        return new OnboardingDetailResponseDto(
                onboardingProfile.getCurrentLevel(),
                onboardingProfile.getGoalText(),
                onboardingProfile.getDailyGoalMinutes(),
                onboardingProfile.getWeeklyGoalCount(),
                SurveyAnswersResponseDto.from(onboardingProfile.getSurveyAnswers()),
                onboardingProfile.getCompletedAt()
        );
    }
}
