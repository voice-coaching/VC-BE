package org.example.voice.analysis.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.application.AnalysisRunPodCallbackService;
import org.example.voice.analysis.controller.dto.*;
import org.example.voice.analysis.domain.type.AnalysisResultIngestionDisposition;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/ai")
@Hidden
public class InternalRunPodAnalysisController {
    private final RunPodInternalAuthentication authentication;
    private final AnalysisRunPodCallbackService callbackService;
    private final RunPodContract contract;
    private final RunPodBackendReadiness readiness;
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalBackendReadiness canonicalReadiness;
    private final org.example.voice.analysis.application.CanonicalCallbackService canonicalCallbacks;

    @GetMapping("/worker-readiness")
    public ResponseEntity<Readiness> readiness(HttpServletRequest request) {
        authenticate(request);
        boolean ready = readiness.isReady();
        return ResponseEntity.status(ready ? 200 : 503)
                .body(new Readiness(ready ? "ready" : "not_ready", RunPodContract.VERSION, now()));
    }

    @PostMapping("/analyses/{analysisId}/claim")
    public RunPodAnalysisControlResponseDto claim(@PathVariable Long analysisId, HttpServletRequest request) throws IOException {
        var json = body(request, "claimRequest");
        var command = contract.convert(json, RunPodAnalysisClaimRequestDto.class).toCommand();
        return RunPodAnalysisControlResponseDto.from(callbackService.claim(analysisId, command));
    }

    @PostMapping("/analyses/{analysisId}/heartbeat")
    public RunPodAnalysisControlResponseDto heartbeat(@PathVariable Long analysisId, HttpServletRequest request) throws IOException {
        var json = body(request, "heartbeatRequest");
        var command = contract.convert(json, RunPodAnalysisHeartbeatRequestDto.class).toCommand();
        return RunPodAnalysisControlResponseDto.from(callbackService.heartbeat(analysisId, command));
    }

    @PostMapping("/analyses/{analysisId}/result")
    public RunPodAnalysisResultCallbackResponseDto result(@PathVariable Long analysisId, HttpServletRequest request) throws IOException {
        authenticate(request);
        throw new RunPodContractException(410,"HANDOFF_REQUIRED");
    }

    private JsonNode body(HttpServletRequest request, String kind) throws IOException {
        return contract.parse(rawBody(request,kind),kind);
    }

    @PostMapping("/analyses/{analysisId}/result/ack")
    public ResponseEntity<RunPodAnalysisResultCallbackResponseDto> confirmCanonicalResult(
            @PathVariable Long analysisId, HttpServletRequest request) throws IOException {
        authenticate(request);
        throw new RunPodContractException(410,"HANDOFF_REQUIRED");
    }

    private byte[] rawBody(HttpServletRequest request, String kind) throws IOException {
        authenticate(request);
        int limit = kind.equals("result") ? RunPodContract.RESULT_LIMIT : RunPodContract.CONTROL_LIMIT;
        return RunPodRequestBody.readJson(request, limit);
    }

    private static String now() { return RunPodContract.timestamp(OffsetDateTime.now(ZoneOffset.UTC)); }
    private void authenticate(HttpServletRequest request) {
        var values = java.util.Collections.list(request.getHeaders("Authorization"));
        if (values.size() != 1) throw new RunPodContractException(401, "UNAUTHENTICATED");
        authentication.verify(values.getFirst());
    }
    public record Readiness(String status, String contractVersion, String serverTime) {}
}
