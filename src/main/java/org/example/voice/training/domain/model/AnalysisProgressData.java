package org.example.voice.training.domain.model;

import org.example.voice.analysis.domain.type.AnalysisStatus;

import java.time.OffsetDateTime;

public record AnalysisProgressData(
        Long analysisId,
        AnalysisStatus status,
        String stage,
        Integer progressPercent,
        String failureReason,
        OffsetDateTime updatedAt
) {
}
