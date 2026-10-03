package org.example.voice.training.domain.model;

import org.example.voice.analysis.domain.type.AnalysisStatus;

import java.time.OffsetDateTime;

public record AnalysisProgressData(
        Long analysisId,
        AnalysisStatus status,
        String stage,
        Integer progressPercent,
        String failureReason,
        OffsetDateTime updatedAt,
        OffsetDateTime deadlineAt,
        OffsetDateTime serverTime
) {
    public AnalysisProgressData(Long analysisId, AnalysisStatus status, String stage,
            Integer progressPercent, String failureReason, OffsetDateTime updatedAt) {
        this(analysisId, status, stage, progressPercent, failureReason, updatedAt, null, null);
    }
}
