package org.example.voice.training.controller.dto;

public record BrowserVideoGrantRequest(
        String clientRequestId, Source source,
        Boolean videoProcessingConsentAccepted, String videoProcessingConsentPolicyRevision) {
    public record Source(String sha256, Long fileSizeBytes, String mimeType) {}
}
