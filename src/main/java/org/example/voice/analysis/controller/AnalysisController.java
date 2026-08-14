package org.example.voice.analysis.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.application.AnalysisSegmentService;
import org.example.voice.analysis.application.AnalysisService;
import org.example.voice.analysis.application.FeedbackRegenerationService;
import org.example.voice.analysis.controller.dto.AnalysisResultResponseDto;
import org.example.voice.analysis.controller.dto.AnalysisSegmentResponseDto;
import org.example.voice.analysis.controller.dto.FeedbackRegenerateRequestDto;
import org.example.voice.analysis.controller.dto.FeedbackRegenerateResponseDto;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analyses")
public class AnalysisController {
    private final AnalysisService analysisService;
    private final AnalysisSegmentService segmentService;
    private final FeedbackRegenerationService feedbackService;

    @GetMapping("/{analysisId}")
    public ApiResponse<AnalysisResultResponseDto> getResult(@PathVariable Long analysisId, @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.success("종합 분석 결과를 조회했습니다.", AnalysisResultResponseDto.from(analysisService.getCompleted(analysisId, user.id())));
    }

    @GetMapping("/{analysisId}/segments")
    public ApiResponse<AnalysisSegmentResponseDto> getSegments(@PathVariable Long analysisId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int size,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.success("음절별 분석 결과를 조회했습니다.", AnalysisSegmentResponseDto.from(segmentService.getSegments(analysisId, user.id(), page, size)));
    }

    @PostMapping("/{analysisId}/feedback/regenerate")
    public ApiResponse<FeedbackRegenerateResponseDto> regenerate(@PathVariable Long analysisId,
            @Valid @RequestBody FeedbackRegenerateRequestDto request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.success("종합 피드백을 다시 생성했습니다.", FeedbackRegenerateResponseDto.from(feedbackService.regenerate(analysisId, user.id(), request.feedbackStyle())));
    }
}
