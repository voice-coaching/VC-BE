package org.example.voice.analysis.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.application.CanonicalEvidenceRegistrationService;
import org.example.voice.analysis.application.CanonicalEvidenceRegistrationService.Receipt;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.example.voice.analysis.infrastructure.runpod.RunPodInternalAuthentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Hidden
@RequestMapping("/api/internal/ai/analyses/{analysisId}/evidence")
public class CanonicalEvidenceController {
    private final RunPodInternalAuthentication authentication;
    private final CanonicalEvidenceRegistrationService receipts;

    @PostMapping
    public ResponseEntity<Receipt> register(@PathVariable long analysisId,HttpServletRequest request) throws IOException {
        UUID worker=authenticate(request);
        var registered=receipts.register(analysisId,worker,RunPodRequestBody.readJson(request,RunPodContract.CONTROL_LIMIT));
        return response(registered.created()?202:200,registered.receipt());
    }

    @GetMapping("/{receiptId}")
    public ResponseEntity<Receipt> status(@PathVariable long analysisId,@PathVariable UUID receiptId,HttpServletRequest request) {
        return response(200,receipts.status(analysisId,receiptId,authenticate(request)));
    }

    private UUID authenticate(HttpServletRequest request) {
        var tokens=Collections.list(request.getHeaders("Authorization"));
        if(tokens.size()!=1) throw new RunPodContractException(401,"UNAUTHENTICATED");
        authentication.verify(tokens.getFirst());
        var workers=Collections.list(request.getHeaders("X-Worker-Instance-Id"));
        if(workers.size()!=1 || !workers.getFirst().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new RunPodContractException(422,"VALIDATION_FAILED");
        return UUID.fromString(workers.getFirst());
    }
    private ResponseEntity<Receipt> response(int status,Receipt receipt) {
        var builder=ResponseEntity.status(status).header("Cache-Control","no-store");
        if (receipt.status().equals("PENDING") || receipt.status().equals("VERIFYING")) builder.header("Retry-After","1");
        return builder.body(receipt);
    }
}
