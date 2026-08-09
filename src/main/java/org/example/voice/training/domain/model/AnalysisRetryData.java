package org.example.voice.training.domain.model;

import org.example.voice.analysis.domain.type.AnalysisStatus;

import java.time.OffsetDateTime;

public record AnalysisRetryData(
        Long analysisId,
        AnalysisStatus status,
        Integer retryCount,
        OffsetDateTime requestedAt
) {
}
