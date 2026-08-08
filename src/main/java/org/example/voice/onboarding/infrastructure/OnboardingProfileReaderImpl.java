package org.example.voice.onboarding.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.onboarding.domain.OnboardingProfile;
import org.example.voice.onboarding.domain.OnboardingProfileReader;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OnboardingProfileReaderImpl implements OnboardingProfileReader {

    private final OnboardingProfileJpaRepository onboardingProfileJpaRepository;

    @Override
    public Optional<OnboardingProfile> findByUserId(Long userId) {
        return onboardingProfileJpaRepository.findByUserId(userId);
    }
}
