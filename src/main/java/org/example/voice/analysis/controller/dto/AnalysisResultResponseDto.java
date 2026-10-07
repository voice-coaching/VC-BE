package org.example.voice.analysis.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.voice.analysis.application.FeedbackRegenerationService;
import org.example.voice.analysis.domain.model.AnalysisResultData;
import org.example.voice.analysis.domain.model.AnalysisScoreBreakdown;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisResultResponseDto(Long id, String status, String outcome, String transcript, BigDecimal sttConfidence,
        BigDecimal overallScore, BigDecimal pronunciationScore, BigDecimal intonationScore, BigDecimal speedWpm,
        String speedStatus, BigDecimal stressScore, BigDecimal pauseScore, List<String> strengths,
        List<String> weaknesses, String summaryFeedback,
        PronunciationEvidenceResponseDto pronunciationEvidence,
        VisualSupplementResponseDto visualSupplement,
        OffsetDateTime analyzedAt, AnalysisScoreBreakdown scoreBreakdown,
        org.example.voice.analysis.domain.model.AnalysisScoreHierarchy scoreHierarchy, AnalysisCoachingResponseDto coaching) {
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
                data.analyzedAt(),
                data.scoreBreakdown(),
                data.scoreHierarchy(),
                AnalysisCoachingResponseDto.from(data.coaching())
        );
    }

    public Object toResponseBody() {
        if (overallScore != null) {
            return this;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        put(body, "id", id);
        put(body, "status", status);
        put(body, "outcome", outcome);
        put(body, "transcript", transcript);
        put(body, "sttConfidence", sttConfidence);
        put(body, "pronunciationScore", pronunciationScore);
        put(body, "intonationScore", intonationScore);
        put(body, "speedWpm", speedWpm);
        put(body, "speedStatus", speedStatus);
        put(body, "stressScore", stressScore);
        put(body, "pauseScore", pauseScore);
        put(body, "strengths", strengths);
        put(body, "weaknesses", weaknesses);
        put(body, "summaryFeedback", summaryFeedback);
        put(body, "pronunciationEvidence", pronunciationEvidence);
        put(body, "visualSupplement", visualSupplement);
        put(body, "analyzedAt", analyzedAt);
        put(body, "scoreBreakdown", scoreBreakdown);
        put(body, "scoreHierarchy", scoreHierarchy);
        put(body, "coaching", coaching);
        return body;
    }

    private static void put(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }
}
