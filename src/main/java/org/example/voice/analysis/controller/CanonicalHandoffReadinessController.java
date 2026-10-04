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
    private final RunPodInternalAuthentication authentication;
    private final org.example.voice.analysis.application.CanonicalCapabilitiesService capabilities;
    @Hidden
    @GetMapping("/api/internal/ai/worker-readiness/handoff")
    public ResponseEntity<Map<String,Object>> internal(HttpServletRequest request){
        var tokens=Collections.list(request.getHeaders("Authorization"));if(tokens.size()!=1)throw new RunPodContractException(401,"UNAUTHENTICATED");authentication.verify(tokens.getFirst());
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("contractVersion","voice-coaching.canonical-handoff.v1","supported",readiness.supported(),"admissionEnabled",readiness.admission(),"schemaDigests",readiness.digests()));
    }
    @GetMapping("/api/analysis-capabilities/canonical")
    public ResponseEntity<ApiResponse<org.example.voice.analysis.domain.model.CanonicalCapabilitiesData>> publicCapabilities(){
        return ResponseEntity.ok().header("Cache-Control","no-store").body(ApiResponse.success("Analysis contracts",capabilities.read()));
    }
}
