package org.example.voice.analysis.domain.model;

import org.example.voice.analysis.domain.type.AnalysisOutcome;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.domain.type.SpeedStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AnalysisResultData(
        Long id,
        AnalysisStatus status,
        AnalysisOutcome outcome,
        String transcript,
        BigDecimal sttConfidence,
        BigDecimal overallScore,
        BigDecimal pronunciationScore,
        BigDecimal intonationScore,
        BigDecimal speedWpm,
        SpeedStatus speedStatus,
        BigDecimal stressScore,
        BigDecimal pauseScore,
        String strengthsText,
        String weaknessesText,
        String summaryFeedback,
        OffsetDateTime analyzedAt
) {

    public boolean isCompleted() {
        return status == AnalysisStatus.COMPLETED;
    }
}
