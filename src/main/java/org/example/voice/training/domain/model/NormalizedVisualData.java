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
        if (objectKey == null || objectKey.isBlank() || objectKey.length() > 1_000) {
            throw new IllegalArgumentException("normalized visual object key is invalid");
        }
        if (!CANONICAL_MIME_TYPE.equals(mimeType)) {
            throw new IllegalArgumentException("normalized visual media must be canonical MP4");
        }
        if (fileSizeBytes == null || fileSizeBytes <= 0) {
            throw new IllegalArgumentException("normalized visual file size is invalid");
        }
        if (visualSha256 == null || !visualSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("normalized visual digest is invalid");
        }
        if (consentReceiptSha256 == null || !consentReceiptSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("visual consent receipt is invalid");
        }
        if (consentPolicyRevision == null || consentPolicyRevision.isBlank()
                || consentPolicyRevision.length() > 100) {
            throw new IllegalArgumentException("visual consent policy revision is invalid");
        }
    }
}
