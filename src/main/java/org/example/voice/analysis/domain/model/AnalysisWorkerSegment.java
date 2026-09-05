package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.voice.analysis.domain.type.SegmentMatchType;
import org.example.voice.analysis.domain.type.SegmentResultStatus;

import java.math.BigDecimal;
import java.util.Objects;

/** Versioned per-segment worker payload, validated before persistence. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AnalysisWorkerSegment(
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
    public AnalysisWorkerSegment {
        if (sequenceNo == null || sequenceNo < 1) {
            throw new IllegalArgumentException("sequenceNo must be positive");
        }
        requireLength(expectedText, 100, "expectedText");
        requireLength(recognizedText, 100, "recognizedText");
        requireLength(targetUnit, 100, "targetUnit");
        requireLength(errorType, 50, "errorType");
        requireLength(feedback, 1_000, "feedback");
        if (startMs != null && startMs < 0 || endMs != null && endMs < 0) {
            throw new IllegalArgumentException("segment offsets must not be negative");
        }
        if (startMs != null && endMs != null && startMs >= endMs) {
            throw new IllegalArgumentException("segment endMs must be after startMs");
        }
        Objects.requireNonNull(matchType, "matchType");
        Objects.requireNonNull(resultStatus, "resultStatus");
        requireScore(pronunciationScore, "pronunciationScore");
        requireScore(intonationScore, "intonationScore");
    }

    private static void requireLength(String value, int maxLength, String field) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
    }

    private static void requireScore(BigDecimal value, String field) {
        if (value != null && (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException(field + " must be between 0 and 100");
        }
    }
}
