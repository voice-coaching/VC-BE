package org.example.voice.analysis.controller.dto;

public record RunPodAnalysisResultCallbackResponseDto(
        java.util.UUID eventId,
        Long analysisId,
        java.util.UUID requestId,
        java.util.UUID executionId,
        String status,
        String serverTime
) {
}
