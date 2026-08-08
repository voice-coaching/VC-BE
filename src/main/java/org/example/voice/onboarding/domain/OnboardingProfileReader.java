package org.example.voice.onboarding.domain;

import java.util.Optional;

public interface OnboardingProfileReader {

    Optional<OnboardingProfile> findByUserId(Long userId);
}
