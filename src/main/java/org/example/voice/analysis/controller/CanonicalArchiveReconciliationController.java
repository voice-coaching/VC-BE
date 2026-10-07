package org.example.voice.analysis.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.infrastructure.canonical.*;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Operations-only token, distinct from the Pod callback token; never permits an arbitrary key or PUT. */
@Hidden
@RestController @RequiredArgsConstructor
public class CanonicalArchiveReconciliationController {
    private final CanonicalArchiveWorker archive;
    private final CanonicalHandoffSettings settings;
    private final RunPodContract contract;
    @PostMapping("/api/internal/ai/archives/{handoffId}/artifacts/{kind}/reconciliation")
    public ResponseEntity<Void> reconcile(@PathVariable UUID handoffId,@PathVariable String kind,HttpServletRequest request)throws java.io.IOException {
        var values=Collections.list(request.getHeaders("Authorization"));String token=settings.value("reconciliation-token");
        if(token.length()<32 || values.size()!=1 || !MessageDigest.isEqual(("Bearer "+token).getBytes(StandardCharsets.UTF_8),values.getFirst().getBytes(StandardCharsets.UTF_8)))throw new RunPodContractException(401,"UNAUTHENTICATED");
        var doc=contract.parse(RunPodRequestBody.readJson(request,8192),"archiveReconciliation");
        archive.reconcile(handoffId,kind,doc.path("versionId").asText());
        return ResponseEntity.noContent().header("Cache-Control","no-store").build();
    }
}
