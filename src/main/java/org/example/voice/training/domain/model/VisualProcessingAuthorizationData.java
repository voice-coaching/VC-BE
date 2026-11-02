package org.example.voice.training.domain.model;

/** Consent evidence required before backend video parsing or retention. */
public record VisualProcessingAuthorizationData(
        String receiptSha256,
        String policyRevision
) {
    public VisualProcessingAuthorizationData {

    }
}
