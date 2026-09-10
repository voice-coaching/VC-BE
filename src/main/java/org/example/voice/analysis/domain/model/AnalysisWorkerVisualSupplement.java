package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Privacy-safe projection of an approved same-attempt visual supplement. */
@JsonIgnoreProperties(ignoreUnknown = false)
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
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("visual supplement schema is unsupported");
        }
        if (selectedExpectedIndex == null || selectedExpectedIndex < 0) {
            throw new IllegalArgumentException("visual selected phone index is invalid");
        }
        if (!"supports_upstream".equals(evidenceRelation)) {
            throw new IllegalArgumentException("visual evidence may only supplement upstream evidence");
        }
        requireIdentifier(approvedClaimId, "approvedClaimId");
        requireIdentifier(rendererKey, "rendererKey");
        requireSha256(upstreamPhoneAnchorRef, "upstreamPhoneAnchorRef");
        requireSha256(supplementSha256, "supplementSha256");
        closedBetaLipObservation = closedBetaLipObservation == null
                ? null
                : validatedObservation(closedBetaLipObservation, selectedExpectedIndex);
    }

    private static Map<String, Object> validatedObservation(
            Map<String, Object> observation, int selectedExpectedIndex
    ) {
        if (!OBSERVATION_FIELDS.equals(observation.keySet())
                || !"voice-coaching.closed-beta-lip-observation.v1".equals(observation.get("schemaVersion"))
                || !"OBSERVED".equals(observation.get("status"))
                || !Boolean.FALSE.equals(observation.get("containsPronunciationTruth"))
                || !Boolean.FALSE.equals(observation.get("containsActionTruth"))
                || !(observation.get("measurements") instanceof Map<?, ?> measurements)
                || measurements.isEmpty()
                || !MEASUREMENT_UNITS.keySet().containsAll(measurements.keySet())) {
            throw new IllegalArgumentException("closed beta lip observation is invalid");
        }
        long observedIndex = requireNonNegativeInteger(observation.get("selectedExpectedIndex"));
        long startMs = requireNonNegativeInteger(observation.get("videoStartMs"));
        long endMs = requireNonNegativeInteger(observation.get("videoEndMs"));
        if (observedIndex != selectedExpectedIndex || endMs <= startMs) {
            throw new IllegalArgumentException("closed beta lip observation is invalid");
        }
        if (!(observation.get("geometryArtifactSha256") instanceof String digest)) {
            throw new IllegalArgumentException("closed beta lip observation is invalid");
        }
        requireSha256(digest, "geometryArtifactSha256");
        Map<String, Object> immutableMeasurements = new HashMap<>();
        for (Map.Entry<?, ?> entry : measurements.entrySet()) {
            if (!(entry.getKey() instanceof String cueId)
                    || !(entry.getValue() instanceof Map<?, ?> measurement)
                    || !MEASUREMENT_FIELDS.equals(measurement.keySet())
                    || !(measurement.get("value") instanceof Number number)
                    || !MEASUREMENT_UNITS.get(cueId).equals(measurement.get("unit"))) {
                throw new IllegalArgumentException("closed beta lip observation is invalid");
            }
            double value = number.doubleValue();
            if (!Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("closed beta lip observation is invalid");
            }
            immutableMeasurements.put(cueId, Map.of("value", value, "unit", MEASUREMENT_UNITS.get(cueId)));
        }
        Map<String, Object> copy = new HashMap<>(observation);
        copy.put("selectedExpectedIndex", selectedExpectedIndex);
        copy.put("videoStartMs", startMs);
        copy.put("videoEndMs", endMs);
        copy.put("measurements", Map.copyOf(immutableMeasurements));
        return Map.copyOf(copy);
    }

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
        if (value < 0) {
            throw new IllegalArgumentException("closed beta lip observation integer is invalid");
        }
        return value;
    }

    private static void requireIdentifier(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:+-]{0,191}")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private static void requireSha256(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }
}
