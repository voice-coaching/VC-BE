package org.example.voice.onboarding.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.onboarding.application.OnboardingService;
import org.example.voice.onboarding.controller.dto.OnboardingDetailResponseDto;
import org.example.voice.onboarding.controller.dto.OnboardingPatchResponseDto;
import org.example.voice.onboarding.controller.dto.OnboardingResponseDto;
import org.example.voice.onboarding.controller.dto.OnboardingSaveRequestDto;
import org.example.voice.onboarding.controller.dto.OnboardingUpdateRequestDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PutMapping("/me")
    public ApiResponse<OnboardingResponseDto> saveMyOnboarding(
            @AuthenticationPrincipal LoginUser user,
            @RequestBody OnboardingSaveRequestDto request
    ) {
        OnboardingResponseDto response = onboardingService.saveMyOnboarding(user.id(), request);
        return ApiResponse.success("온보딩이 완료되었습니다.", response);
    }

    @GetMapping("/me")
    public ApiResponse<OnboardingDetailResponseDto> getMyOnboarding(@AuthenticationPrincipal LoginUser user) {
        OnboardingDetailResponseDto response = onboardingService.getMyOnboarding(user.id());
        return ApiResponse.success("온보딩 정보를 조회했습니다.", response);
    }

    @PatchMapping("/me")
    public ApiResponse<OnboardingPatchResponseDto> updateMyOnboarding(
            @AuthenticationPrincipal LoginUser user,
            @RequestBody OnboardingUpdateRequestDto request
    ) {
        OnboardingPatchResponseDto response = onboardingService.updateMyOnboarding(user.id(), request);
        return ApiResponse.success("온보딩 정보가 수정되었습니다.", response);
    }
}
