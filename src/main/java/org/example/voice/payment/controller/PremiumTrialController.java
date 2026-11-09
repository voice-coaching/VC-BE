package org.example.voice.payment.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.payment.application.PremiumTrialService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/premium-trial")
public class PremiumTrialController {
    private final PremiumTrialService trials;

    @GetMapping
    public ResponseEntity<ApiResponse<PremiumTrialService.Status>> get(@AuthenticationPrincipal LoginUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("프리미엄 체험 상태를 조회했습니다.", trials.status(user.id())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PremiumTrialService.Status>> activate(@AuthenticationPrincipal LoginUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("72시간 프리미엄 체험이 시작됐습니다.", trials.activate(user.id())));
    }
}
