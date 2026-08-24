package org.example.voice.analysis.controller.dto;

import org.example.voice.analysis.application.FeedbackRegenerationService;
import org.example.voice.analysis.domain.model.AnalysisResultData;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AnalysisResultResponseDto(Long id, String status, String transcript, BigDecimal sttConfidence,
        BigDecimal overallScore, BigDecimal pronunciationScore, BigDecimal intonationScore, BigDecimal speedWpm,
        String speedStatus, BigDecimal stressScore, BigDecimal pauseScore, List<String> strengths,
        List<String> weaknesses, String summaryFeedback, OffsetDateTime analyzedAt) {
    public static AnalysisResultResponseDto from(AnalysisResultData data) {
        return new AnalysisResultResponseDto(
                data.id(),
                data.status().name(),
                data.transcript(),
                data.sttConfidence(),
                data.overallScore(),
                data.pronunciationScore(),
                data.intonationScore(),
                data.speedWpm(),
                data.speedStatus(),
                data.stressScore(),
                data.pauseScore(),
                FeedbackRegenerationService.split(data.strengthsText()),
                FeedbackRegenerationService.split(data.weaknessesText()),
                data.summaryFeedback(),
                data.analyzedAt()
        );
    }
}
