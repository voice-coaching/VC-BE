package org.example.voice.analysis.domain.model;

import java.math.BigDecimal;

/** Stored same-attempt evidence safe for the owned analysis response. */
public record PronunciationEvidenceData(
        String schemaVersion,
        String selectedPhone,
        Integer selectedExpectedIndex,
        Integer selectedStartMs,
        Integer selectedEndMs,
        BigDecimal detectorScore,
        BigDecimal operatingThreshold,
        String scoreSemantics,
        String evidenceState
) {
}
