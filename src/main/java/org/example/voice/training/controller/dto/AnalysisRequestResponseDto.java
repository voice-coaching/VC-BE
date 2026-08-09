package org.example.voice.training.controller.dto;

import org.example.voice.training.domain.model.AnalysisRequestData;

import java.time.OffsetDateTime;

public record AnalysisRequestResponseDto(
        Long analysisId,
        String status,
        OffsetDateTime requestedAt
) {

    public static AnalysisRequestResponseDto from(AnalysisRequestData data) {
        return new AnalysisRequestResponseDto(data.analysisId(), data.status().name(), data.requestedAt());
    }
}
