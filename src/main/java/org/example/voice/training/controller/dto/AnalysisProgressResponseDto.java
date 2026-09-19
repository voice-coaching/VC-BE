package org.example.voice.training.controller.dto;

import org.example.voice.training.domain.model.AnalysisProgressData;

import java.time.OffsetDateTime;

public record AnalysisProgressResponseDto(
        Long analysisId,
        String status,
        String stage,
        Integer progressPercent,
        String failureReason,
        OffsetDateTime updatedAt
) {

    public static AnalysisProgressResponseDto from(AnalysisProgressData data) {
        return new AnalysisProgressResponseDto(
                data.analysisId(),
                data.status().name(),
                data.stage(),
                data.progressPercent(),
                data.failureReason(),
                data.updatedAt()
        );
    }
}
