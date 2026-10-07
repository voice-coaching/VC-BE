package org.example.voice.analysis.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.infrastructure.canonical.CanonicalAudiovisualReadiness;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

/** Uses the existing internal AI server authentication boundary. */
@RestController @RequiredArgsConstructor
public class CanonicalAudiovisualReadinessController {
    private final CanonicalAudiovisualReadiness readiness;
    private final org.example.voice.analysis.infrastructure.runpod.RunPodInternalAuthentication authentication;
    @GetMapping("/api/internal/ai/worker-readiness/audiovisual")
    public ResponseEntity<Map<String,Object>> get(jakarta.servlet.http.HttpServletRequest request){
        var tokens=java.util.Collections.list(request.getHeaders("Authorization"));
        if(tokens.size()!=1)throw new org.example.voice.analysis.infrastructure.runpod.RunPodContractException(401,"UNAUTHENTICATED");
        authentication.verify(tokens.getFirst());
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of(
            "contractVersion","voice-coaching.canonical-handoff.v2", "supported",readiness.supported(),
            "schemaDigests",readiness.digests()));
    }
}
