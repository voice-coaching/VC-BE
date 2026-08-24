package org.example.voice.analysis.domain.model;

import org.example.voice.analysis.domain.type.AnalysisStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AnalysisResultData(
        Long id,
        AnalysisStatus status,
        String transcript,
        BigDecimal sttConfidence,
        BigDecimal overallScore,
        BigDecimal pronunciationScore,
        BigDecimal intonationScore,
        BigDecimal speedWpm,
        String speedStatus,
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
