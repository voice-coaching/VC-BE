package org.example.voice.training.controller.dto;

public record BrowserVideoGrantResponse(
        String schemaVersion, String aiBaseUrl, String jobsPath,
        String grantToken, long expiresAt, UploadMetadata metadata) {
    public record UploadMetadata(String clientRequestId, String scriptText, Long contentId,
                                 BrowserVideoGrantRequest.Source source) {}
    @Override public String toString() { return "BrowserVideoGrantResponse[redacted]"; }
}
