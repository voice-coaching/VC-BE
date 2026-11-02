package org.example.voice.training.domain.model;

/** Canonical, restricted visual object retained only for the associated analysis. */
public record NormalizedVisualData(
        String objectKey,
        String mimeType,
        Long fileSizeBytes,
        String visualSha256,
        String consentReceiptSha256,
        String consentPolicyRevision
) {
    public static final String CANONICAL_MIME_TYPE = "video/mp4";

    public NormalizedVisualData {

    }
}
