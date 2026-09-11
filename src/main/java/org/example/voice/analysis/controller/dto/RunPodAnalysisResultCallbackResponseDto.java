package org.example.voice.analysis.controller.dto;

public record RunPodAnalysisResultCallbackResponseDto(
        Long analysisId,
        String status
) {
}
