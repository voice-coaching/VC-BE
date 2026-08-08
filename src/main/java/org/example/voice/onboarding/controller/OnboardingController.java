package org.example.voice.onboarding.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.onboarding.application.OnboardingService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;
}
