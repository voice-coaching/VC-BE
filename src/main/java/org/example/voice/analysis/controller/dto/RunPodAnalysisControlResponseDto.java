package org.example.voice.analysis.controller.dto;

import org.example.voice.analysis.domain.model.AnalysisRunPodControlData;

import java.util.UUID;

public record RunPodAnalysisControlResponseDto(
        Long analysisId,
        UUID requestId,
        UUID executionId,
        Boolean accepted,
        Boolean canceled,
        Boolean continueProcessing
) {
    public static RunPodAnalysisControlResponseDto from(AnalysisRunPodControlData data) {
        return new RunPodAnalysisControlResponseDto(
                data.analysisId(),
                data.requestId(),
                data.executionId(),
                data.accepted(),
                data.canceled(),
                data.continueProcessing()
        );
    }
}
