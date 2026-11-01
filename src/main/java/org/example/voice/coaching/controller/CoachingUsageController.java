package org.example.voice.coaching.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.coaching.application.CoachingUsageService;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CoachingUsageController {
    private final CoachingUsageService usage;

    @GetMapping("/api/users/me/coaching-usage")
    public ResponseEntity<ApiResponse<CoachingUsageResponse>> get(@AuthenticationPrincipal LoginUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("코칭 사용량을 조회했습니다.", CoachingUsageResponse.from(usage.getUsage(user.id()))));
    }
}
