package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Same-attempt selected-phone evidence owned by the Seungun pipeline. */
@JsonIgnoreProperties(ignoreUnknown = false)
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
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("pronunciation evidence schemaVersion is unsupported");
        }
        if (selectedPhone == null || selectedPhone.isBlank() || selectedPhone.length() > 16) {
            throw new IllegalArgumentException("selectedPhone is invalid");
        }
        if (selectedExpectedIndex == null || selectedExpectedIndex < 0) {
            throw new IllegalArgumentException("selectedExpectedIndex must not be negative");
        }
        if ((selectedStartMs == null) != (selectedEndMs == null)) {
            throw new IllegalArgumentException("selected phone offsets must be paired");
        }
        if (selectedStartMs != null && (selectedStartMs < 0 || selectedEndMs <= selectedStartMs)) {
            throw new IllegalArgumentException("selected phone offsets are invalid");
        }
        if (detectorScore == null || operatingThreshold == null
                || detectorScore.signum() < 0 || detectorScore.compareTo(BigDecimal.ONE) > 0
                || operatingThreshold.signum() < 0 || operatingThreshold.compareTo(BigDecimal.ONE) > 0
                || detectorScore.compareTo(operatingThreshold) < 0) {
            throw new IllegalArgumentException("detector evidence values are invalid");
        }
        if (!SCORE_SEMANTICS.equals(scoreSemantics)) {
            throw new IllegalArgumentException("scoreSemantics is unsupported");
        }
        if (!EVIDENCE_STATE.equals(evidenceState)) {
            throw new IllegalArgumentException("evidenceState is unsupported");
        }
    }
}
