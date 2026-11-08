package org.example.voice.analysis.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.example.voice.analysis.application.DirectHistoryService;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.example.voice.analysis.infrastructure.runpod.RunPodInternalAuthentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Hidden
@RestController
public class InternalDirectHistoryController {
    private final DirectHistoryService service;
    private final RunPodInternalAuthentication authentication;
    private final ObjectMapper mapper;

    public InternalDirectHistoryController(DirectHistoryService service,
                                          RunPodInternalAuthentication authentication, ObjectMapper mapper) {
        this.service = service;
        this.authentication = authentication;
        this.mapper = mapper;
    }

    @PostMapping("/api/internal/ai/direct-analysis-history")
    public ResponseEntity<Map<String, String>> receive(HttpServletRequest request) throws IOException {
        var headers = Collections.list(request.getHeaders("Authorization"));
        if(headers.size()!=1) throw new RunPodContractException(401,"UNAUTHENTICATED");
        authentication.verify(headers.getFirst());
        byte[] raw = RunPodRequestBody.readJson(request, 1024 * 1024);
        tools.jackson.databind.JsonNode document;
        try {
            document = mapper.readTree(raw);
        } catch (JacksonException error) {
            throw new RunPodContractException(400, "INVALID_JSON");
        }

        service.receive(document);
        return ResponseEntity.accepted().body(Map.of("state", "RECEIVED"));
    }
}
