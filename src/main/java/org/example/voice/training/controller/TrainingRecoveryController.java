package org.example.voice.training.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.training.application.TrainingRecoveryService;
import org.example.voice.training.controller.dto.TrainingRecoveryResponseDto;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/training-sessions")
public class TrainingRecoveryController {
    private final TrainingRecoveryService recovery;

    @GetMapping("/{sessionId}/recovery")
    public ResponseEntity<ApiResponse<TrainingRecoveryResponseDto>> get(@AuthenticationPrincipal LoginUser user,
                                                                       @PathVariable Long sessionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(
                "학습 업로드와 분석 복구 정보를 조회했습니다.", TrainingRecoveryResponseDto.from(recovery.get(sessionId, user.id()))));
    }
}
