package org.example.voice.onboarding.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.onboarding.domain.OnboardingProfileReader;
import org.example.voice.onboarding.domain.OnboardingProfileWriter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final OnboardingProfileReader onboardingProfileReader;
    private final OnboardingProfileWriter onboardingProfileWriter;
}
