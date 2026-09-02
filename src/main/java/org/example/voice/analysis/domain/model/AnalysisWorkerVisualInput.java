package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Optional, consent-bound canonical video for same-attempt visual supplementation. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AnalysisWorkerVisualInput(
        String objectKey,
        String sha256,
        String mimeType,
        Long fileSizeBytes,
        String consentReceiptSha256,
        String consentPolicyRevision
) {
    public AnalysisWorkerVisualInput {
        if (objectKey == null || objectKey.isBlank() || objectKey.length() > 1_000
                || objectKey.startsWith("/") || objectKey.startsWith("http://")
                || objectKey.startsWith("https://") || objectKey.contains("\\")
                || java.util.Arrays.stream(objectKey.split("/"))
                        .anyMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals("..")
                                || segment.equalsIgnoreCase("users")
                                || segment.equalsIgnoreCase("sessions"))) {
            throw new IllegalArgumentException("visual object key is invalid");
        }
        requireSha256(sha256, "visual digest");
        if (!"video/mp4".equals(mimeType)) {
            throw new IllegalArgumentException("visual mime type must be canonical MP4");
        }
        if (fileSizeBytes == null || fileSizeBytes <= 0) {
            throw new IllegalArgumentException("visual file size must be positive");
        }
        requireSha256(consentReceiptSha256, "visual consent receipt");
        if (consentPolicyRevision == null || consentPolicyRevision.isBlank()
                || consentPolicyRevision.length() > 100) {
            throw new IllegalArgumentException("visual consent policy revision is invalid");
        }
    }

    private static void requireSha256(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }
}
