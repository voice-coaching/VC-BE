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
        var json = body(request, "result");
        var dto = contract.convert(json, RunPodAnalysisResultCallbackRequestDto.class);
        var disposition = callbackService.ingestResult(analysisId, dto.toCommand(contract.digest(json)));
        return new RunPodAnalysisResultCallbackResponseDto(dto.eventId(), analysisId, dto.requestId(), dto.executionId(),
                disposition == AnalysisResultIngestionDisposition.IGNORED_DUPLICATE ? "DUPLICATE" : "APPLIED", now());
    }

    private JsonNode body(HttpServletRequest request, String kind) throws IOException {
        authenticate(request);
        if (request.getContentType() == null || !request.getContentType().split(";")[0].trim().equals("application/json")
                || (request.getHeader("Content-Encoding") != null && !"identity".equals(request.getHeader("Content-Encoding")))) {
            throw new RunPodContractException(415, "UNSUPPORTED_MEDIA_TYPE");
        }
        int limit = kind.equals("result") ? RunPodContract.RESULT_LIMIT : RunPodContract.CONTROL_LIMIT;
        if (request.getContentLengthLong() > limit) throw new RunPodContractException(413, "PAYLOAD_TOO_LARGE");
        return contract.parse(request.getInputStream().readNBytes(limit + 1), kind);
    }

    private static String now() { return RunPodContract.timestamp(OffsetDateTime.now(ZoneOffset.UTC)); }
    private void authenticate(HttpServletRequest request) {
        var values = java.util.Collections.list(request.getHeaders("Authorization"));
        if (values.size() != 1) throw new RunPodContractException(401, "UNAUTHENTICATED");
        authentication.verify(values.getFirst());
    }
    public record Readiness(String status, String contractVersion, String serverTime) {}
}
