package org.example.voice.analysis.controller.dto;

import org.example.voice.analysis.domain.entity.AnalysisResult;
import java.math.BigDecimal;

public record AnalysisStatusResponseDto(Long sessionId, Long analysisId, String status, BigDecimal overallScore,
        BigDecimal pronunciationScore, BigDecimal intonationScore) {
    public static AnalysisStatusResponseDto from(Long sessionId, AnalysisResult r) {
        return new AnalysisStatusResponseDto(sessionId, r.getId(), r.getStatus().name(), r.getOverallScore(), r.getPronunciationScore(), r.getIntonationScore());
    }
}
