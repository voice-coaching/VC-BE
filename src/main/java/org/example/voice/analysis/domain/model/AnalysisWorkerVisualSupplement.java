package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Privacy-safe projection of an approved same-attempt visual supplement. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisWorkerVisualSupplement(
        String schemaVersion,
        Integer selectedExpectedIndex,
        String evidenceRelation,
        String approvedClaimId,
        String rendererKey,
        String upstreamPhoneAnchorRef,
        String supplementSha256,
        Map<String, Object> closedBetaLipObservation
) {
    public static final String SCHEMA_VERSION = "voice-coaching.visual-supplement.v1";
    private static final Set<String> OBSERVATION_FIELDS = Set.of(
            "schemaVersion", "status", "selectedExpectedIndex", "videoStartMs", "videoEndMs",
            "geometryArtifactSha256", "measurements", "containsPronunciationTruth", "containsActionTruth"
    );
    private static final Map<String, String> MEASUREMENT_UNITS = Map.of(
            "mouth_width_fraction", "image_fraction",
            "outer_aperture_ratio", "ratio",
            "inner_aperture_ratio", "ratio",
            "outer_area_ratio", "ratio",
            "inner_area_ratio", "ratio",
            "corner_height_asymmetry_ratio", "ratio",
            "shape_change_rate_per_second", "normalized_per_second"
    );
    private static final Set<String> MEASUREMENT_FIELDS = Set.of("value", "unit");

    public AnalysisWorkerVisualSupplement(
            String schemaVersion,
            Integer selectedExpectedIndex,
            String evidenceRelation,
            String approvedClaimId,
            String rendererKey,
            String upstreamPhoneAnchorRef,
            String supplementSha256
    ) {
        this(schemaVersion, selectedExpectedIndex, evidenceRelation, approvedClaimId,
                rendererKey, upstreamPhoneAnchorRef, supplementSha256, null);
    }

    public AnalysisWorkerVisualSupplement {

        closedBetaLipObservation = closedBetaLipObservation == null
                ? null
                : validatedObservation(closedBetaLipObservation, selectedExpectedIndex);
    }

    private static Map<String, Object> validatedObservation(
            Map<String, Object> observation, int selectedExpectedIndex
    ) { return new HashMap<>(observation); }

    private static long requireNonNegativeInteger(Object raw) {
        final long value;
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
            value = ((Number) raw).longValue();
        } else if (raw instanceof BigInteger integer) {
            try {
                value = integer.longValueExact();
            } catch (ArithmeticException error) {
                throw new IllegalArgumentException("closed beta lip observation integer is invalid");
            }
        } else {
            throw new IllegalArgumentException("closed beta lip observation integer is invalid");
        }

        return value;
    }

}
