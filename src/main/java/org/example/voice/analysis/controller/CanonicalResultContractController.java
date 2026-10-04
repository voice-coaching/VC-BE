package org.example.voice.analysis.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.application.CanonicalAnalysisQueryService;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Current owned attempt routing, no stored URL or archive status in the public contract. */
@RestController @RequiredArgsConstructor
public class CanonicalResultContractController {
    private final CanonicalAnalysisQueryService queries;
    public record Contract(long analysisId,long recordingId,UUID requestId,UUID executionId,String analysisProfile,String resultSchemaVersion) {}
    @GetMapping("/api/analyses/{analysisId}/result-contract")
    public ResponseEntity<ApiResponse<Contract>> contract(@PathVariable Long analysisId,@AuthenticationPrincipal LoginUser user){
        var view=queries.get(analysisId,user.id());
        var schema=RunPodContract.RESULT_V5;
        return ResponseEntity.ok().header("Cache-Control","no-store").body(ApiResponse.success("Analysis contract",new Contract(view.analysisId(),view.recordingId(),view.requestId(),view.executionId(),view.analysisProfile(),schema)));
    }
}
