package org.example.voice.onboarding.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.onboarding.controller.dto.OnboardingDetailResponseDto;
import org.example.voice.onboarding.controller.dto.OnboardingPatchResponseDto;
import org.example.voice.onboarding.controller.dto.OnboardingResponseDto;
import org.example.voice.onboarding.controller.dto.OnboardingSaveRequestDto;
import org.example.voice.onboarding.controller.dto.OnboardingUpdateRequestDto;
import org.example.voice.onboarding.domain.entity.OnboardingProfile;
import org.example.voice.onboarding.domain.port.OnboardingProfileReader;
import org.example.voice.onboarding.domain.port.OnboardingProfileWriter;
import org.example.voice.onboarding.domain.entity.SurveyAnswers;
import org.example.voice.onboarding.exception.OnboardingNotFoundException;
import org.example.voice.onboarding.exception.RequiredAnswerMissingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final Long TEMP_LOGIN_USER_ID = 1L;

    private final OnboardingProfileReader onboardingProfileReader;
    private final OnboardingProfileWriter onboardingProfileWriter;

    @Transactional
    public OnboardingResponseDto saveMyOnboarding(OnboardingSaveRequestDto request) {
        validateRequiredAnswers(request);

        Long userId = TEMP_LOGIN_USER_ID;
        SurveyAnswers surveyAnswers = SurveyAnswers.from(request.surveyAnswers());
        OnboardingProfile profile = onboardingProfileReader.findByUserId(userId)
                .map(existingProfile -> {
                    existingProfile.update(
                            request.currentLevel(),
                            request.goalText(),
                            request.dailyGoalMinutes(),
                            request.weeklyGoalCount(),
                            surveyAnswers
                    );
                    return existingProfile;
                })
                .orElseGet(() -> OnboardingProfile.create(
                        userId,
                        request.currentLevel(),
                        request.goalText(),
                        request.dailyGoalMinutes(),
                        request.weeklyGoalCount(),
                        surveyAnswers
                ));

        OnboardingProfile savedProfile = onboardingProfileWriter.save(profile);
        return OnboardingResponseDto.from(savedProfile);
    }

    @Transactional(readOnly = true)
    public OnboardingDetailResponseDto getMyOnboarding() {
        Long userId = TEMP_LOGIN_USER_ID;
        OnboardingProfile profile = onboardingProfileReader.findByUserId(userId)
                .orElseThrow(OnboardingNotFoundException::new);
        return OnboardingDetailResponseDto.from(profile);
    }

    @Transactional
    public OnboardingPatchResponseDto updateMyOnboarding(OnboardingUpdateRequestDto request) {
        Long userId = TEMP_LOGIN_USER_ID;
        OnboardingProfile profile = onboardingProfileReader.findByUserId(userId)
                .orElseThrow(OnboardingNotFoundException::new);
        profile.patch(
                request.goalText(),
                request.dailyGoalMinutes(),
                request.weeklyGoalCount(),
                request.surveyAnswers()
        );
        OnboardingProfile savedProfile = onboardingProfileWriter.save(profile);
        return OnboardingPatchResponseDto.from(savedProfile);
    }

    private void validateRequiredAnswers(OnboardingSaveRequestDto request) {
        if (request == null || request.currentLevel() == null || request.surveyAnswers() == null) {
            throw new RequiredAnswerMissingException();
        }
        if (isMissing(request.surveyAnswers().learningPurposes())
                || isMissing(request.surveyAnswers().improvementAreas())
                || isMissing(request.surveyAnswers().pronunciationConcerns())
                || isMissing(request.surveyAnswers().learningSituations())) {
            throw new RequiredAnswerMissingException();
        }
    }

    private boolean isMissing(java.util.List<String> values) {
        return values == null || values.isEmpty();
    }
}
