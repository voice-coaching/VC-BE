package org.example.voice.training.domain.model;

import org.example.voice.analysis.domain.type.AnalysisStatus;

import java.time.OffsetDateTime;

public record AnalysisRequestData(
        Long analysisId,
        AnalysisStatus status,
        OffsetDateTime requestedAt
) {
}
