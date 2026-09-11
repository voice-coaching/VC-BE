package org.example.voice.analysis.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.application.AnalysisRunPodCallbackService;
import org.example.voice.analysis.controller.dto.RunPodAnalysisClaimRequestDto;
import org.example.voice.analysis.controller.dto.RunPodAnalysisControlResponseDto;
import org.example.voice.analysis.controller.dto.RunPodAnalysisHeartbeatRequestDto;
import org.example.voice.analysis.controller.dto.RunPodAnalysisResultCallbackRequestDto;
import org.example.voice.analysis.controller.dto.RunPodAnalysisResultCallbackResponseDto;
import org.example.voice.analysis.domain.type.AnalysisResultIngestionDisposition;
import org.example.voice.analysis.infrastructure.runpod.RunPodInternalAuthentication;
import org.example.voice.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/ai/analyses")
@Hidden
public class InternalRunPodAnalysisController {

    private final RunPodInternalAuthentication authentication;
    private final AnalysisRunPodCallbackService callbackService;

    @PostMapping("/{analysisId}/claim")
    public ResponseEntity<ApiResponse<RunPodAnalysisControlResponseDto>> claim(
            @PathVariable Long analysisId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RunPodAnalysisClaimRequestDto request
    ) {
        authentication.verify(authorization);
        return ResponseEntity.ok(ApiResponse.success(
                "AI 분석 실행 점유를 확인했습니다.",
                RunPodAnalysisControlResponseDto.from(callbackService.claim(analysisId, request.toCommand()))
        ));
    }

    @PostMapping("/{analysisId}/heartbeat")
    public ResponseEntity<ApiResponse<RunPodAnalysisControlResponseDto>> heartbeat(
            @PathVariable Long analysisId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RunPodAnalysisHeartbeatRequestDto request
    ) {
        authentication.verify(authorization);
        return ResponseEntity.ok(ApiResponse.success(
                "AI 분석 실행 상태를 갱신했습니다.",
                RunPodAnalysisControlResponseDto.from(callbackService.heartbeat(analysisId, request.toCommand()))
        ));
    }

    @PostMapping("/{analysisId}/result")
    public ResponseEntity<ApiResponse<RunPodAnalysisResultCallbackResponseDto>> result(
            @PathVariable Long analysisId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RunPodAnalysisResultCallbackRequestDto request
    ) {
        authentication.verify(authorization);
        AnalysisResultIngestionDisposition disposition = callbackService.ingestResult(analysisId, request.toCommand());
        return ResponseEntity.ok(ApiResponse.success(
                disposition == AnalysisResultIngestionDisposition.IGNORED_DUPLICATE
                        ? "AI 분석 결과를 이미 수신했습니다."
                        : "AI 분석 결과를 수신했습니다.",
                new RunPodAnalysisResultCallbackResponseDto(analysisId, request.status().name())
        ));
    }
}
