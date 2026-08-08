package org.example.voice.onboarding.infrastructure;

import org.example.voice.onboarding.domain.OnboardingProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface OnboardingProfileJpaRepository extends JpaRepository<OnboardingProfile, Long> {

    Optional<OnboardingProfile> findByUserId(Long userId);
}
