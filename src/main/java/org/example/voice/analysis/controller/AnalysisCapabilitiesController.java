package org.example.voice.analysis.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.application.AnalysisCapabilitiesService;
import org.example.voice.analysis.controller.dto.AnalysisCapabilitiesResponseDto;
import org.example.voice.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analysis-capabilities")
public class AnalysisCapabilitiesController {
    private final AnalysisCapabilitiesService service;

    @Operation(summary = "녹음·분석 지원 조건 조회", description = "인증된 사용자가 업로드 형식·한도·동의 정책과 연결 설정 여부를 조회합니다. 실시간 인프라 상태 확인은 수행하지 않습니다.")
    @GetMapping
    public ApiResponse<AnalysisCapabilitiesResponseDto> getCapabilities() {
        return ApiResponse.success(
                "녹음 및 분석 지원 조건을 조회했습니다.",
                AnalysisCapabilitiesResponseDto.from(service.getCapabilities())
        );
    }
}
