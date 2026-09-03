package org.example.voice.analysis.infrastructure.redis;

import java.math.BigDecimal;
import java.util.List;

record AnalysisResultStreamMessageDto(
        Long analysisId,
        String status,
        String transcript,
        BigDecimal sttConfidence,
        String sttModelName,
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
        String failureReason,
        List<SegmentDto> segments
) {

    record SegmentDto(
            Integer sequenceNo,
            String expectedText,
            String recognizedText,
            Integer startMs,
            Integer endMs,
            String matchType,
            String resultStatus,
            String targetUnit,
            String errorType,
            BigDecimal pronunciationScore,
            BigDecimal intonationScore,
            String feedback
    ) {
    }
}
