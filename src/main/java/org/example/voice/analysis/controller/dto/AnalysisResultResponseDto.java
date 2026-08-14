package org.example.voice.analysis.controller.dto;

import org.example.voice.analysis.application.FeedbackRegenerationService;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AnalysisResultResponseDto(Long id, String status, String transcript, BigDecimal sttConfidence,
        BigDecimal overallScore, BigDecimal pronunciationScore, BigDecimal intonationScore, BigDecimal speedWpm,
        String speedStatus, BigDecimal stressScore, BigDecimal pauseScore, List<String> strengths,
        List<String> weaknesses, String summaryFeedback, OffsetDateTime analyzedAt) {
    public static AnalysisResultResponseDto from(AnalysisResult r) {
        return new AnalysisResultResponseDto(r.getId(), r.getStatus().name(), r.getTranscript(), r.getSttConfidence(), r.getOverallScore(), r.getPronunciationScore(), r.getIntonationScore(), r.getSpeedWpm(), r.getSpeedStatus(), r.getStressScore(), r.getPauseScore(), FeedbackRegenerationService.split(r.getStrengthsText()), FeedbackRegenerationService.split(r.getWeaknessesText()), r.getSummaryFeedback(), r.getAnalyzedAt());
    }
}
