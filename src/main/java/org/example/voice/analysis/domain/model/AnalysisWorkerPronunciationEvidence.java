package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Same-attempt selected-phone evidence owned by the Seungun pipeline. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisWorkerPronunciationEvidence(
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
    public static final String SCHEMA_VERSION = "voice-coaching.pronunciation-evidence.v1";
    public static final String SCORE_SEMANTICS =
            "detector_ranking_score_not_calibrated_correctness_confidence";
    public static final String EVIDENCE_STATE = "frozen_detector_threshold_passed";

    public AnalysisWorkerPronunciationEvidence {

    }
}
