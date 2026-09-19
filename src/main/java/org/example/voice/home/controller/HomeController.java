package org.example.voice.home.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.home.application.HomeService;
import org.example.voice.home.controller.dto.HomeDashboardResponseDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;

    @GetMapping
    public ApiResponse<HomeDashboardResponseDto> getHomeDashboard(@AuthenticationPrincipal LoginUser user) {
        HomeDashboardResponseDto response = homeService.getHomeDashboard(user.id());
        return ApiResponse.success("홈 정보를 조회했습니다.", response);
    }
}
