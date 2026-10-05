package org.example.voice.analysis.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.voice.analysis.application.DirectHistoryService;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;

@RestController
@ConditionalOnProperty(name="analysis.direct-history.enabled",havingValue="true")
public class DirectHistoryController {
    private final DirectHistoryService service;
    private final RunPodInternalAuthentication authentication;
    private final ObjectMapper mapper;
    public DirectHistoryController(DirectHistoryService service, RunPodInternalAuthentication authentication,ObjectMapper mapper){
        this.service=service;this.authentication=authentication;this.mapper=mapper;
    }
    @PostMapping("/api/internal/ai/direct-analysis-history")
    public ResponseEntity<Map<String,String>> receive(HttpServletRequest request)throws IOException {
        var headers=Collections.list(request.getHeaders("Authorization"));
        if(headers.size()!=1)throw new RunPodContractException(401,"AUTHENTICATION_REQUIRED");
        authentication.verify(headers.getFirst());
        service.receive(mapper.readTree(RunPodRequestBody.readJson(request,1024*1024)));
        return ResponseEntity.accepted().body(Map.of("state","RECEIVED"));
    }
    public record LinkRequest(String historyClaim){}
    @PostMapping("/api/direct-analysis-history/{jobId}/link")
    public ApiResponse<Map<String,String>> link(@AuthenticationPrincipal LoginUser user,@PathVariable UUID jobId,@RequestBody LinkRequest request){
        return ApiResponse.success("분석 이력 연결",Map.of("state",service.link(user.id(),jobId,request.historyClaim())));
    }
    @GetMapping("/api/direct-analysis-history/{jobId}")
    public ApiResponse<Map<String,String>> state(@AuthenticationPrincipal LoginUser user,@PathVariable UUID jobId){
        return ApiResponse.success("분석 저장 상태",Map.of("state",service.state(user.id(),jobId)));
    }
    @GetMapping("/api/direct-analysis-history")
    public ApiResponse<List<Map<String,Object>>> list(@AuthenticationPrincipal LoginUser user){
        return ApiResponse.success("직접 분석 이력",service.list(user.id()));
    }
}
