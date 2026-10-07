package org.example.voice.analysis.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.infrastructure.canonical.*;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;

@Hidden
@RestController @RequiredArgsConstructor
@RequestMapping("/api/internal/ai/analyses/{analysisId}/handoffs")
public class CanonicalHandoffController {
    private final RunPodInternalAuthentication authentication;
    private final RunPodContract contract;
    private final org.example.voice.analysis.application.CanonicalHandoffService store;
    @PostMapping("/result") public ResponseEntity<Map<String,Object>> publish(@PathVariable long analysisId,HttpServletRequest request)throws IOException {
        var worker=authenticate(request);
        var doc=CanonicalCallbackDocument.parse(RunPodRequestBody.readJson(request,1024*1024),contract);
        return ResponseEntity.accepted().header("Cache-Control","no-store").body(store.publish(analysisId,worker,doc));
    }
    @PostMapping public ResponseEntity<CanonicalHandoffStore.Snapshot> receive(@PathVariable long analysisId,HttpServletRequest request)throws IOException {
        var worker=authenticate(request);
        var doc=CanonicalHandoffDocument.parse(RunPodRequestBody.readJson(request,2*1024*1024),contract);
        return ResponseEntity.accepted().header("Cache-Control","no-store").body(store.receive(analysisId,worker,doc));
    }
    @PutMapping("/{handoffId}/artifacts/{kind}") public ResponseEntity<Void> stage(@PathVariable long analysisId,@PathVariable UUID handoffId,@PathVariable String kind,HttpServletRequest request)throws IOException {
        var worker=authenticate(request);var raw=RunPodRequestBody.readBinary(request,16*1024*1024);
        store.stage(analysisId,handoffId,worker,kind,raw);return ResponseEntity.noContent().build();
    }
    @PostMapping("/{handoffId}/seal") public CanonicalHandoffStore.Snapshot seal(@PathVariable long analysisId,@PathVariable UUID handoffId,HttpServletRequest request)throws IOException {
        var worker=authenticate(request);
        if(request.getInputStream().read()!=-1)throw new RunPodContractException(422,"VALIDATION_FAILED");
        return store.seal(analysisId,handoffId,worker);
    }
    @GetMapping("/{handoffId}") public ResponseEntity<CanonicalHandoffStore.Snapshot> status(@PathVariable long analysisId,@PathVariable UUID handoffId,HttpServletRequest request) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(store.status(analysisId,handoffId,authenticate(request)));
    }
    private UUID authenticate(HttpServletRequest request) {
        authentication.verify(one(request,"Authorization"));
        String worker=one(request,"X-Worker-Instance-Id");
        if(!worker.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new RunPodContractException(422,"VALIDATION_FAILED");
        return UUID.fromString(worker);
    }
    private static String one(HttpServletRequest request,String name){var values=Collections.list(request.getHeaders(name));if(values.size()!=1)throw new RunPodContractException(name.equals("Authorization")?401:422,"VALIDATION_FAILED");return values.getFirst();}
}
