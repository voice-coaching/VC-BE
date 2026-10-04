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

    @GetMapping("/worker-readiness/v2")
    public ResponseEntity<RunPodWorkerReadinessV2ResponseDto> readinessV2(HttpServletRequest request) {
        authenticate(request);
        var digests = org.example.voice.analysis.infrastructure.canonical.CanonicalBackendReadiness.SCHEMAS.stream()
                .map(file -> new RunPodWorkerReadinessV2ResponseDto.SchemaDigestDto(file, contract.schemaSha256(file)))
                .toList();
        boolean supported=canonicalReadiness.supported();
        boolean admission=canonicalReadiness.admissionEnabled();
        var versions=new java.util.ArrayList<>(List.of("voice-coaching.runpod-analysis-result.v1",
                "voice-coaching.runpod-analysis-result.v2","voice-coaching.runpod-analysis-result.v3"));
        var profiles=new java.util.ArrayList<>(List.of("LEGACY_SEUNGUN_V3"));
        if(supported){versions.add(RunPodContract.RESULT_V4);profiles.add("CANONICAL_FROZEN_20260928_V4");}
        return ResponseEntity.status(supported ? 200 : 503).header("Cache-Control", "no-store")
                .body(new RunPodWorkerReadinessV2ResponseDto(
                        supported ? "ready" : "not_ready", RunPodContract.CAPABILITY_VERSION, now(), readiness.isReady(),
                        versions, profiles, digests, supported, admission,
                        supported ? "READY" : "NOT_READY", supported ? "READY" : "NOT_READY",
                        supported ? null : "NOT_READY"));
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
        byte[] raw = rawBody(request, "result");
        var json = contract.parse(raw, "result");
        if (RunPodContract.RESULT_V4.equals(json.path("schemaVersion").asText())) {
            var document=org.example.voice.analysis.infrastructure.canonical.CanonicalCallbackDocument.parse(raw,contract);
            var disposition=canonicalCallbacks.ingest(analysisId,document);
            return canonicalCallbacks.acknowledgement(analysisId,document,
                    disposition==AnalysisResultIngestionDisposition.IGNORED_DUPLICATE?"DUPLICATE":"APPLIED");
        }
        if(RunPodContract.RESULT_V5.equals(json.path("schemaVersion").asText()))throw new RunPodContractException(422,"HANDOFF_REQUIRED");
        var dto = contract.convert(json, RunPodAnalysisResultCallbackRequestDto.class);
        var disposition = callbackService.ingestResult(analysisId, dto.toCommand(contract.digest(json)));
        return new RunPodAnalysisResultCallbackResponseDto(dto.eventId(), analysisId, dto.requestId(), dto.executionId(),
                disposition == AnalysisResultIngestionDisposition.IGNORED_DUPLICATE ? "DUPLICATE" : "APPLIED", now());
    }

    private JsonNode body(HttpServletRequest request, String kind) throws IOException {
        return contract.parse(rawBody(request,kind),kind);
    }

    @PostMapping("/analyses/{analysisId}/result/ack")
    public ResponseEntity<RunPodAnalysisResultCallbackResponseDto> confirmCanonicalResult(
            @PathVariable Long analysisId, HttpServletRequest request) throws IOException {
        var document=org.example.voice.analysis.infrastructure.canonical.CanonicalCallbackDocument.parse(
                rawBody(request,"result"),contract);
        return ResponseEntity.ok().header("Cache-Control","no-store").body(
                canonicalCallbacks.acknowledgement(analysisId,document,"DUPLICATE"));
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
