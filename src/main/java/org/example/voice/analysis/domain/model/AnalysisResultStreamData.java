package org.example.voice.analysis.domain.model;

import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.domain.type.SegmentMatchType;
import org.example.voice.analysis.domain.type.SegmentResultStatus;
import org.example.voice.analysis.domain.type.SpeedStatus;

import java.math.BigDecimal;
import java.util.List;

public record AnalysisResultStreamData(
        Long analysisId,
        AnalysisStatus status,
        String transcript,
        BigDecimal sttConfidence,
        String sttModelName,
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
        String failureReason,
        List<Segment> segments
) {

    public record Segment(
            Integer sequenceNo,
            String expectedText,
            String recognizedText,
            Integer startMs,
            Integer endMs,
            SegmentMatchType matchType,
            SegmentResultStatus resultStatus,
            String targetUnit,
            String errorType,
            BigDecimal pronunciationScore,
            BigDecimal intonationScore,
            String feedback
    ) {
    }
}
