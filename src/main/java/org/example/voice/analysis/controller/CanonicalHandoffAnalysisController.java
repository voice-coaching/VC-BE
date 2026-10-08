package org.example.voice.analysis.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.model.CanonicalAnalysisView;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Hidden
@RequestMapping("/api/v3/analyses")
public class CanonicalHandoffAnalysisController {
    private final org.example.voice.analysis.application.CanonicalResultAwaitService awaiting;

    @GetMapping("/{analysisId}")
    public java.util.concurrent.CompletableFuture<ResponseEntity<ApiResponse<CanonicalAnalysisView>>> get(@PathVariable Long analysisId,
            @RequestParam(defaultValue="0") int waitSeconds, @AuthenticationPrincipal LoginUser user) {
        return awaiting.get(analysisId,user.id(),waitSeconds).thenApply(view->ResponseEntity.ok().header("Cache-Control","no-store")
                .body(ApiResponse.success("분석 상태와 근거를 조회했습니다.",v5(view))));
    }
    private static CanonicalAnalysisView v5(CanonicalAnalysisView view) {
        if(!org.example.voice.analysis.infrastructure.runpod.RunPodContract.handoffProfile(view.analysisProfile()))throw new org.example.voice.analysis.exception.CanonicalAnalysisViewException(org.example.voice.analysis.exception.CanonicalAnalysisViewException.Reason.CANONICAL_ANALYSIS_NOT_FOUND);
        return view;
    }
}
