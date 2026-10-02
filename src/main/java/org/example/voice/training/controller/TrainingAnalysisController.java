package org.example.voice.training.controller;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.training.application.TrainingAnalysisRequestService;
import org.example.voice.training.application.TrainingAnalysisSchemaAdmissionService;
import org.example.voice.analysis.domain.type.AnalysisExecutionProfile;
import org.example.voice.training.exception.AnalysisSchemaAdmissionException;
import org.example.voice.training.controller.dto.AnalysisProgressResponseDto;
import org.example.voice.training.controller.dto.AnalysisRequestResponseDto;
import org.example.voice.training.controller.dto.AnalysisRetryResponseDto;
import org.example.voice.training.controller.dto.AnalysisConsentRequestDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/training-sessions")
public class TrainingAnalysisController {

    private final TrainingAnalysisRequestService trainingAnalysisRequestService;
    private final TrainingAnalysisSchemaAdmissionService schemaAdmissionService;

    @PostMapping("/{sessionId}/analyze")
    public ApiResponse<AnalysisRequestResponseDto> requestAnalysis(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long sessionId,
            @Valid @RequestBody AnalysisConsentRequestDto request,
            HttpServletRequest httpRequest
    ) {
        var profile = requestedProfile(httpRequest);
        return ApiResponse.success(
                "음성 분석을 요청했습니다.",
                AnalysisRequestResponseDto.from(
                        trainingAnalysisRequestService.requestAnalysis(sessionId, user.id(), request.toData(), profile)
                )
        );
    }

    @GetMapping("/{sessionId}/analysis/status")
    public ApiResponse<AnalysisProgressResponseDto> getAnalysisStatus(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.success(
                "분석 진행 상태를 조회했습니다.",
                AnalysisProgressResponseDto.from(trainingAnalysisRequestService.getStatus(sessionId, user.id()))
        );
    }

    @PostMapping("/{sessionId}/analysis/retry")
    public ApiResponse<AnalysisRetryResponseDto> retryAnalysis(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long sessionId,
            @Valid @RequestBody AnalysisConsentRequestDto request,
            HttpServletRequest httpRequest
    ) {
        var profile = requestedProfile(httpRequest);
        return ApiResponse.success(
                "음성 분석을 다시 요청했습니다.",
                AnalysisRetryResponseDto.from(
                        trainingAnalysisRequestService.retry(sessionId, user.id(), request.toData(), profile)
                )
        );
    }

    private AnalysisExecutionProfile requestedProfile(HttpServletRequest request) {
        var values = Collections.list(request.getHeaders("X-Analysis-Result-Schema"));
        if (values.size() > 1) throw new AnalysisSchemaAdmissionException(ErrorCode.INVALID_INPUT_VALUE);
        // A comma-joined repeated header also fails the service's exact supported-value check.
        return schemaAdmissionService.selectProfile(values.isEmpty() ? null : values.getFirst());
    }
}
