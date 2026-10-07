package org.example.voice.analysis.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.application.CanonicalAnalysisQueryService;
import org.example.voice.analysis.domain.model.CanonicalResultContract;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Current owned attempt routing, no stored URL or archive status in the public contract. */
@RestController @RequiredArgsConstructor
public class CanonicalResultContractController {
    private final CanonicalAnalysisQueryService queries;
    @GetMapping("/api/analyses/{analysisId}/result-contract")
    public ResponseEntity<ApiResponse<CanonicalResultContract>> contract(@PathVariable Long analysisId,@AuthenticationPrincipal LoginUser user){
        return ResponseEntity.ok().header("Cache-Control","no-store")
                .body(ApiResponse.success("Analysis contract",queries.contract(analysisId,user.id())));
    }
}
