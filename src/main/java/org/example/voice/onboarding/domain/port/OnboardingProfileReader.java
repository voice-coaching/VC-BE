package org.example.voice.onboarding.domain.port;

import org.example.voice.onboarding.domain.entity.OnboardingProfile;

import java.util.Optional;

public interface OnboardingProfileReader {

    Optional<OnboardingProfile> findByUserId(Long userId);
}
