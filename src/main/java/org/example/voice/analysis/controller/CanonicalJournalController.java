package org.example.voice.analysis.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.infrastructure.canonical.CanonicalBackendJournal;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@RestController
@Hidden
@RequiredArgsConstructor
@RequestMapping("/api/internal/ai/analyses/{analysisId}/journal/{executionId}")
public class CanonicalJournalController {
    private final RunPodInternalAuthentication authentication;
    private final CanonicalBackendJournal journal;
    private final org.example.voice.analysis.application.CanonicalCallbackService callbacks;

    @GetMapping("/callback/ack")
    public ResponseEntity<org.example.voice.analysis.controller.dto.RunPodAnalysisResultCallbackResponseDto> recoverAck(
            @PathVariable long analysisId,@PathVariable UUID executionId,HttpServletRequest request) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(
                callbacks.recoverAcknowledgement(analysisId,executionId,authenticate(request)));
    }

    @PostMapping("/reserve")
    public ResponseEntity<CanonicalBackendJournal.Snapshot> reserve(@PathVariable long analysisId,@PathVariable UUID executionId,
                                                                   HttpServletRequest request) throws IOException {
        UUID worker=authenticate(request);
        var result=journal.reserve(analysisId,executionId,worker,one(request,"X-Worker-Revision"),one(request,"X-Pipeline-Revision"),
                RunPodRequestBody.readJson(request,RunPodContract.CONTROL_LIMIT));
        return ResponseEntity.ok().header("Cache-Control","no-store").body(result);
    }
    @GetMapping
    public ResponseEntity<CanonicalBackendJournal.Snapshot> status(@PathVariable long analysisId,@PathVariable UUID executionId,HttpServletRequest request) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(journal.status(analysisId,executionId,authenticate(request)));
    }
    @PostMapping("/prepare")
    public ResponseEntity<Void> prepare(@PathVariable long analysisId,@PathVariable UUID executionId,HttpServletRequest request) throws IOException {
        UUID worker=authenticate(request);
        journal.prepare(analysisId,executionId,worker,RunPodRequestBody.readJson(request,RunPodContract.CONTROL_LIMIT));return done();
    }
    @PostMapping("/start")
    public ResponseEntity<Void> start(@PathVariable long analysisId,@PathVariable UUID executionId,HttpServletRequest request) throws IOException {
        UUID worker=authenticate(request);empty(request);journal.startProducer(analysisId,executionId,worker);return done();
    }
    @PutMapping("/artifacts/{kind}/stage")
    public ResponseEntity<Void> stage(@PathVariable long analysisId,@PathVariable UUID executionId,@PathVariable String kind,HttpServletRequest request) throws IOException {
        UUID worker=authenticate(request);
        byte[] raw=RunPodRequestBody.readBinary(request,CanonicalBackendJournal.ARTIFACT_LIMIT);
        journal.stage(analysisId,executionId,worker,kind,raw);return done();
    }
    @PostMapping("/artifacts/{kind}/begin")
    public ResponseEntity<Void> begin(@PathVariable long analysisId,@PathVariable UUID executionId,@PathVariable String kind,HttpServletRequest request) throws IOException {
        UUID worker=authenticate(request);empty(request);journal.beginUpload(analysisId,executionId,worker,kind);return done();
    }
    @PostMapping("/artifacts/{kind}/{checkpoint:uploaded|readback}")
    public ResponseEntity<Void> checkpoint(@PathVariable long analysisId,@PathVariable UUID executionId,@PathVariable String kind,
                                           @PathVariable String checkpoint,HttpServletRequest request) throws IOException {
        UUID worker=authenticate(request);
        journal.checkpoint(analysisId,executionId,worker,kind,RunPodRequestBody.readJson(request,8192),checkpoint.equals("readback"));return done();
    }
    @PostMapping("/manifest")
    public ResponseEntity<Void> manifest(@PathVariable long analysisId,@PathVariable UUID executionId,HttpServletRequest request) throws IOException {
        UUID worker=authenticate(request);
        journal.manifest(analysisId,executionId,worker,RunPodRequestBody.readJson(request,RunPodContract.CONTROL_LIMIT));return done();
    }
    private UUID authenticate(HttpServletRequest request) {
        authentication.verify(one(request,"Authorization"));
        String worker=one(request,"X-Worker-Instance-Id");
        if(!worker.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new RunPodContractException(422,"VALIDATION_FAILED");
        return UUID.fromString(worker);
    }
    private static String one(HttpServletRequest request,String name) {
        var values=Collections.list(request.getHeaders(name));
        if(values.size()!=1)throw new RunPodContractException(name.equals("Authorization")?401:422,
                name.equals("Authorization")?"UNAUTHENTICATED":"VALIDATION_FAILED");
        return values.getFirst();
    }
    private static void empty(HttpServletRequest request) throws IOException {
        if(request.getContentLengthLong()>0 || request.getInputStream().read()!=-1)throw new RunPodContractException(422,"VALIDATION_FAILED");
    }
    private static ResponseEntity<Void> done(){return ResponseEntity.noContent().header("Cache-Control","no-store").build();}
}
