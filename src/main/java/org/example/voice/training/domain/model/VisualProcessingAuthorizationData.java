package org.example.voice.training.domain.model;

/** Consent evidence required before backend video parsing or retention. */
public record VisualProcessingAuthorizationData(
        String receiptSha256,
        String policyRevision
) {
    public VisualProcessingAuthorizationData {
        if (receiptSha256 == null || !receiptSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("visual consent receipt is invalid");
        }
        if (policyRevision == null || policyRevision.isBlank() || policyRevision.length() > 100) {
            throw new IllegalArgumentException("visual consent policy revision is invalid");
        }
    }
}
