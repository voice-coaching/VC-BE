package org.example.voice.onboarding.controller.dto;

import org.example.voice.onboarding.domain.OnboardingProfile;

import java.time.OffsetDateTime;

public record OnboardingResponseDto(
        boolean completed,
        OffsetDateTime completedAt
) {

    public static OnboardingResponseDto from(OnboardingProfile onboardingProfile) {
        return new OnboardingResponseDto(
                onboardingProfile.getCompletedAt() != null,
                onboardingProfile.getCompletedAt()
        );
    }
}
