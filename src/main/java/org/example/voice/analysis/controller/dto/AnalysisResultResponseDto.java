package org.example.voice.analysis.controller.dto;

import org.example.voice.analysis.application.FeedbackRegenerationService;
import org.example.voice.analysis.domain.model.AnalysisResultData;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AnalysisResultResponseDto(Long id, String status, String outcome, String transcript, BigDecimal sttConfidence,
        BigDecimal overallScore, BigDecimal pronunciationScore, BigDecimal intonationScore, BigDecimal speedWpm,
        String speedStatus, BigDecimal stressScore, BigDecimal pauseScore, List<String> strengths,
        List<String> weaknesses, String summaryFeedback,
        PronunciationEvidenceResponseDto pronunciationEvidence,
        VisualSupplementResponseDto visualSupplement,
        OffsetDateTime analyzedAt) {
    public static AnalysisResultResponseDto from(AnalysisResultData data) {
        return new AnalysisResultResponseDto(
                data.id(),
                data.status().name(),
                data.outcome() == null ? null : data.outcome().name(),
                data.transcript(),
                data.sttConfidence(),
                data.overallScore(),
                data.pronunciationScore(),
                data.intonationScore(),
                data.speedWpm(),
                data.speedStatus() == null ? null : data.speedStatus().name(),
                data.stressScore(),
                data.pauseScore(),
                FeedbackRegenerationService.split(data.strengthsText()),
                FeedbackRegenerationService.split(data.weaknessesText()),
                data.summaryFeedback(),
                PronunciationEvidenceResponseDto.from(data.pronunciationEvidence()),
                VisualSupplementResponseDto.from(data.visualSupplement()),
                data.analyzedAt()
        );
    }
}
