package org.example.voice.onboarding.controller.dto;

import org.example.voice.onboarding.domain.entity.OnboardingProfile;

import java.time.OffsetDateTime;

public record OnboardingPatchResponseDto(
        String goalText,
        Integer dailyGoalMinutes,
        OffsetDateTime updatedAt
) {

    public static OnboardingPatchResponseDto from(OnboardingProfile onboardingProfile) {
        return new OnboardingPatchResponseDto(
                onboardingProfile.getGoalText(),
                onboardingProfile.getDailyGoalMinutes(),
                onboardingProfile.getUpdatedAt()
        );
    }
}
