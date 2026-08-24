package org.example.voice.analysis.controller.dto;

import org.example.voice.analysis.domain.model.AnalysisResultData;

import java.math.BigDecimal;

public record AnalysisStatusResponseDto(Long sessionId, Long analysisId, String status, BigDecimal overallScore,
        BigDecimal pronunciationScore, BigDecimal intonationScore) {
    public static AnalysisStatusResponseDto from(Long sessionId, AnalysisResultData data) {
        return new AnalysisStatusResponseDto(
                sessionId,
                data.id(),
                data.status().name(),
                data.overallScore(),
                data.pronunciationScore(),
                data.intonationScore()
        );
    }
}
