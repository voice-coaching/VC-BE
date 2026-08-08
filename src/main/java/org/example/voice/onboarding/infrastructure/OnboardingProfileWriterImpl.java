package org.example.voice.onboarding.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.onboarding.domain.OnboardingProfile;
import org.example.voice.onboarding.domain.OnboardingProfileWriter;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OnboardingProfileWriterImpl implements OnboardingProfileWriter {

    private final OnboardingProfileJpaRepository onboardingProfileJpaRepository;

    @Override
    public OnboardingProfile save(OnboardingProfile onboardingProfile) {
        return onboardingProfileJpaRepository.save(onboardingProfile);
    }
}
