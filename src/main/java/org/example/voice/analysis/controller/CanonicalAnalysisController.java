package org.example.voice.analysis.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.application.CanonicalAnalysisQueryService;
import org.example.voice.analysis.domain.model.CanonicalAnalysisView;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@Hidden
@RequestMapping("/api/v2/analyses")
public class CanonicalAnalysisController {
    private final CanonicalAnalysisQueryService queries;

    @GetMapping("/{analysisId}")
    public ResponseEntity<ApiResponse<CanonicalAnalysisView>> get(@PathVariable Long analysisId,
            @AuthenticationPrincipal LoginUser user) {
        return ResponseEntity.ok().header("Cache-Control","no-store")
                .body(ApiResponse.success("분석 상태와 근거를 조회했습니다.",v4(queries.get(analysisId,user.id()))));
    }
    private static CanonicalAnalysisView v4(CanonicalAnalysisView view) {
        if(!CanonicalAnalysisView.PROFILE.equals(view.analysisProfile()))throw new org.example.voice.analysis.exception.CanonicalAnalysisViewException(org.example.voice.analysis.exception.CanonicalAnalysisViewException.Reason.CANONICAL_ANALYSIS_NOT_FOUND);
        return view;
    }
}
