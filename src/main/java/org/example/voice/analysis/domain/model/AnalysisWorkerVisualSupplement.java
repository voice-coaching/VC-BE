package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Privacy-safe projection of an approved same-attempt visual supplement. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AnalysisWorkerVisualSupplement(
        String schemaVersion,
        Integer selectedExpectedIndex,
        String evidenceRelation,
        String approvedClaimId,
        String rendererKey,
        String upstreamPhoneAnchorRef,
        String supplementSha256
) {
    public static final String SCHEMA_VERSION = "voice-coaching.visual-supplement.v1";

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
