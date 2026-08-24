package org.example.voice.analysis.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.application.AnalysisService;
import org.example.voice.analysis.controller.dto.AnalysisStatusResponseDto;
import org.example.voice.analysis.domain.model.AnalysisResultData;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/training-sessions")
public class SessionAnalysisController {
    private final AnalysisService analysisService;

    @GetMapping("/{sessionId}/analysis")
    public ApiResponse<AnalysisStatusResponseDto> getSessionAnalysis(@PathVariable Long sessionId, @AuthenticationPrincipal LoginUser user) {
        AnalysisResultData result = analysisService.getBySession(sessionId, user.id());
        return ApiResponse.success("학습 세션의 분석 결과를 조회했습니다.", AnalysisStatusResponseDto.from(sessionId, result));
    }
}
