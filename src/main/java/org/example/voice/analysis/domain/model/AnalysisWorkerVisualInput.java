package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Optional, consent-bound canonical video for same-attempt visual supplementation. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisWorkerVisualInput(
        String objectKey,
        String sha256,
        String mimeType,
        Long fileSizeBytes,
        String consentReceiptSha256,
        String consentPolicyRevision
) {
    public AnalysisWorkerVisualInput {

    }

}
