package org.example.voice.analysis.controller;

import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Hidden;
import org.example.voice.analysis.infrastructure.canonical.CanonicalAudiovisualReadiness;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

/** Uses the existing internal AI server authentication boundary. */
@Hidden
@RestController @RequiredArgsConstructor
public class CanonicalAudiovisualReadinessController {
    private final CanonicalAudiovisualReadiness readiness;
    private final org.example.voice.analysis.infrastructure.runpod.RunPodInternalAuthentication authentication;
    @GetMapping("/api/internal/ai/worker-readiness/audiovisual")
    public ResponseEntity<Map<String,Object>> get(jakarta.servlet.http.HttpServletRequest request){
        var tokens=java.util.Collections.list(request.getHeaders("Authorization"));

        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of(
            "contractVersion","voice-coaching.canonical-handoff.v2", "supported",readiness.supported(),
            "schemaDigests",readiness.digests()));
    }
}
