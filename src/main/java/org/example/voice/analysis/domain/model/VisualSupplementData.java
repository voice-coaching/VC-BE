package org.example.voice.analysis.domain.model;

/** Stored public view of an approved same-attempt visual supplement. */
public record VisualSupplementData(
        String schemaVersion,
        Integer selectedExpectedIndex,
        String evidenceRelation,
        String approvedClaimId,
        String rendererKey,
        String upstreamPhoneAnchorRef,
        String supplementSha256
) {
}
