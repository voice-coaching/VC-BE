package org.example.voice.training.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.training.application.BrowserVideoGrantService;
import org.example.voice.training.controller.dto.BrowserVideoGrantRequest;
import org.example.voice.training.controller.dto.BrowserVideoGrantResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/training-sessions")
public class BrowserVideoGrantController {
    private final BrowserVideoGrantService grants;

    @PostMapping("/{sessionId}/video-analysis-upload-grants")
    public ResponseEntity<ApiResponse<BrowserVideoGrantResponse>> issue(@PathVariable Long sessionId,
            @RequestBody BrowserVideoGrantRequest body, HttpServletRequest request) {
        if (Collections.list(request.getHeaders("Authorization")).size()!=1)
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        if (Collections.list(request.getHeaders("Origin")).size()!=1)
            throw new BaseException(ErrorCode.ACCESS_DENIED);
        return ResponseEntity.ok().header("Cache-Control","no-store").body(ApiResponse.success(
                "영상 분석 업로드 권한을 발급했습니다.",
                grants.issue(sessionId,request.getHeader("Authorization"),request.getHeader("Origin"),body)));
    }
}
