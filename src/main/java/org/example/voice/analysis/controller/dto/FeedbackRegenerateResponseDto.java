package org.example.voice.analysis.controller.dto;

import org.example.voice.analysis.application.FeedbackRegenerationService;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import java.time.OffsetDateTime;
import java.util.List;

public record FeedbackRegenerateResponseDto(Long analysisId, List<String> strengths, List<String> weaknesses,
        String summaryFeedback, OffsetDateTime regeneratedAt) {
    public static FeedbackRegenerateResponseDto from(AnalysisResult r) {
        return new FeedbackRegenerateResponseDto(r.getId(), FeedbackRegenerationService.split(r.getStrengthsText()), FeedbackRegenerationService.split(r.getWeaknessesText()), r.getSummaryFeedback(), r.getFeedbackRegeneratedAt());
    }
}
