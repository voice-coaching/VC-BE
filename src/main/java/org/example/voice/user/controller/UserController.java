package org.example.voice.user.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.home.application.RecentLearningService;
import org.example.voice.home.controller.dto.RecentTrainingSessionResponseDto;
import org.example.voice.user.application.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final RecentLearningService recentLearningService;

    @GetMapping("/me/training-sessions/recent")
    public ApiResponse<RecentTrainingSessionResponseDto> getRecentTrainingSession() {
        RecentTrainingSessionResponseDto response = recentLearningService.getRecentTrainingSession();
        return ApiResponse.success("최근 학습 정보를 조회했습니다.", response);
    }
}
