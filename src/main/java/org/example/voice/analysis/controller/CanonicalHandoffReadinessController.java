package org.example.voice.analysis.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.example.voice.analysis.infrastructure.canonical.*;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.example.voice.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor
public class CanonicalHandoffReadinessController {
    private final CanonicalHandoffReadiness readiness;
    private final CanonicalBackendReadiness v4;
    private final RunPodInternalAuthentication authentication;
    @Hidden
    @GetMapping("/api/internal/ai/worker-readiness/handoff")
    public ResponseEntity<Map<String,Object>> internal(HttpServletRequest request){
        var tokens=Collections.list(request.getHeaders("Authorization"));if(tokens.size()!=1)throw new RunPodContractException(401,"UNAUTHENTICATED");authentication.verify(tokens.getFirst());
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("contractVersion","voice-coaching.canonical-handoff.v1","supported",readiness.supported(),"admissionEnabled",readiness.admission(),"schemaDigests",readiness.digests()));
    }
    @GetMapping("/api/analysis-capabilities/canonical")
    public ResponseEntity<ApiResponse<Map<String,Object>>> publicCapabilities(){
        var schemas=new ArrayList<String>();if(v4.admissionEnabled())schemas.add(RunPodContract.RESULT_V4);if(readiness.admission())schemas.add(RunPodContract.RESULT_V5);
        return ResponseEntity.ok().header("Cache-Control","no-store").body(ApiResponse.success("Analysis contracts",Map.of("resultSchemas",schemas)));
    }
}
