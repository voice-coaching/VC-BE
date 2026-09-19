package org.example.voice.user.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.home.application.RecentLearningService;
import org.example.voice.home.controller.dto.RecentTrainingSessionResponseDto;
import org.example.voice.user.application.UserService;
import org.example.voice.user.controller.dto.UserProfileResponseDto;
import org.example.voice.user.controller.dto.UserProfileUpdateRequestDto;
import org.example.voice.user.controller.dto.UserProfileUpdateResponseDto;
import org.example.voice.user.controller.dto.UserWithdrawalResponseDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final RecentLearningService recentLearningService;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponseDto> getMyProfile(@AuthenticationPrincipal LoginUser loginUser) {
        UserProfileResponseDto response = UserProfileResponseDto.from(userService.getMyProfile(loginUser.id()));
        return ApiResponse.success("내 정보를 조회했습니다.", response);
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfileUpdateResponseDto> updateMyProfile(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody UserProfileUpdateRequestDto request
    ) {
        UserProfileUpdateResponseDto response = UserProfileUpdateResponseDto.from(
                userService.updateMyProfile(loginUser.id(), request.nickname()));
        return ApiResponse.success("프로필이 수정되었습니다.", response);
    }

    @DeleteMapping("/me")
    public ApiResponse<UserWithdrawalResponseDto> withdraw(@AuthenticationPrincipal LoginUser loginUser) {
        UserWithdrawalResponseDto response = UserWithdrawalResponseDto.from(userService.withdraw(loginUser.id()));
        return ApiResponse.success("회원 탈퇴가 완료되었습니다.", response);
    }

    @GetMapping("/me/training-sessions/recent")
    public ApiResponse<RecentTrainingSessionResponseDto> getRecentTrainingSession(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        RecentTrainingSessionResponseDto response = recentLearningService.getRecentTrainingSession(loginUser.id());
        return ApiResponse.success("최근 학습 정보를 조회했습니다.", response);
    }
}
