package org.example.voice.analysis.controller.dto;

import org.example.voice.analysis.domain.model.AnalysisResultData;
import org.example.voice.analysis.domain.model.AnalysisScoreBreakdown;

import java.math.BigDecimal;

public record AnalysisStatusResponseDto(Long sessionId, Long analysisId, String status, String outcome, BigDecimal overallScore,
        BigDecimal pronunciationScore, BigDecimal intonationScore, AnalysisScoreBreakdown scoreBreakdown) {
    public static AnalysisStatusResponseDto from(Long sessionId, AnalysisResultData data) {
        return new AnalysisStatusResponseDto(
                sessionId,
                data.id(),
                data.status().name(),
                data.outcome() == null ? null : data.outcome().name(),
                data.overallScore(),
                data.pronunciationScore(),
                data.intonationScore(),
                data.scoreBreakdown()
        );
    }
}
