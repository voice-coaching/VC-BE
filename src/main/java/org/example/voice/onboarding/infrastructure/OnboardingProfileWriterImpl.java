package org.example.voice.onboarding.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.home.infrastructure.cache.HomeCacheNames;
import org.example.voice.onboarding.domain.entity.OnboardingProfile;
import org.example.voice.onboarding.domain.port.OnboardingProfileWriter;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OnboardingProfileWriterImpl implements OnboardingProfileWriter {

    private final OnboardingProfileJpaRepository onboardingProfileJpaRepository;

    @Override
    @CacheEvict(cacheNames = HomeCacheNames.TODAY_STATUS, allEntries = true)
    public OnboardingProfile save(OnboardingProfile onboardingProfile) {
        return onboardingProfileJpaRepository.save(onboardingProfile);
    }
}
