package org.example.voice.onboarding.domain.port;

import org.example.voice.onboarding.domain.entity.OnboardingProfile;

public interface OnboardingProfileWriter {

    OnboardingProfile save(OnboardingProfile onboardingProfile);
}
