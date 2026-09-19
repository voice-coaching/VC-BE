package org.example.voice.analysis.domain.model;

import org.example.voice.analysis.domain.type.SegmentMatchType;
import org.example.voice.analysis.domain.type.SegmentResultStatus;

import java.math.BigDecimal;

public record AnalysisSegmentData(
        Long id,
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
