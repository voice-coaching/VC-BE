package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.voice.analysis.domain.type.SegmentMatchType;
import org.example.voice.analysis.domain.type.SegmentResultStatus;

import java.math.BigDecimal;
import java.util.Objects;

/** Versioned per-segment worker payload, validated before persistence. */
@JsonIgnoreProperties(ignoreUnknown = true)
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

        Objects.requireNonNull(matchType, "matchType");
        Objects.requireNonNull(resultStatus, "resultStatus");

    }

}
