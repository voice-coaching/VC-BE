package org.example.voice.training.controller.dto;

import org.example.voice.training.domain.model.AnalysisRetryData;

import java.time.OffsetDateTime;

public record AnalysisRetryResponseDto(
        Long analysisId,
        String status,
        Integer retryCount,
        OffsetDateTime requestedAt
) {

    public static AnalysisRetryResponseDto from(AnalysisRetryData data) {
        return new AnalysisRetryResponseDto(data.analysisId(), data.status().name(), data.retryCount(), data.requestedAt());
    }
}
