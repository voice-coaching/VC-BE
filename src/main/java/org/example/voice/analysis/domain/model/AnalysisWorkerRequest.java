package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.voice.practicecontent.domain.type.LearningFocus;

import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Versioned Backend-to-AI request payload. It contains no user identifier or
 * signed playback URL; the worker resolves the object key through its own
 * restricted storage adapter.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AnalysisWorkerRequest(
        String schemaVersion,
        UUID eventId,
        Long analysisId,
        Long contentId,
        String promptRevision,
        String scriptText,
        String scriptSha256,
        String audioObjectKey,
        String audioSha256,
        String mimeType,
        Long fileSizeBytes,
        Integer durationMs,
        LearningFocus learningFocus,
        AnalysisAuthorizationGrant authorizationGrant
) {
    public static final String SCHEMA_VERSION = "voice-coaching.analysis-request.v3";

    public AnalysisWorkerRequest {
        requireEquals(SCHEMA_VERSION, schemaVersion, "schemaVersion");
        Objects.requireNonNull(eventId, "eventId");
        requirePositive(analysisId, "analysisId");
        requirePositive(contentId, "contentId");
        requireText(promptRevision, 100, "promptRevision");
        requireText(scriptText, 10_000, "scriptText");
        requireSha256(scriptSha256, "scriptSha256");
        requireText(audioObjectKey, 1_000, "audioObjectKey");
        requireSha256(audioSha256, "audioSha256");
        if (audioObjectKey.startsWith("http://") || audioObjectKey.startsWith("https://")
                || audioObjectKey.startsWith("/") || audioObjectKey.contains("\\")
                || hasTraversalSegment(audioObjectKey)) {
            throw new IllegalArgumentException("audioObjectKey must be a relative, non-traversing object key");
        }
        if (!"audio/wav".equals(mimeType)) {
            throw new IllegalArgumentException("mimeType must identify canonical WAV audio");
        }
        if (fileSizeBytes == null || fileSizeBytes <= 0) {
            throw new IllegalArgumentException("fileSizeBytes must be positive");
        }
        if (durationMs == null || durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be positive");
        }
        Objects.requireNonNull(learningFocus, "learningFocus");
        Objects.requireNonNull(authorizationGrant, "authorizationGrant");
        if (!scriptSha256.equals(sha256(scriptText))) {
            throw new IllegalArgumentException("scriptText does not match scriptSha256");
        }
        if (!eventId.equals(authorizationGrant.requestEventId())
                || !analysisId.equals(authorizationGrant.analysisId())
                || !contentId.equals(authorizationGrant.contentId())
                || !promptRevision.equals(authorizationGrant.promptRevision())
                || !scriptSha256.equals(authorizationGrant.scriptSha256())
                || !sha256(audioObjectKey).equals(authorizationGrant.audioObjectKeySha256())
                || !audioSha256.equals(authorizationGrant.audioSha256())
                || !Objects.equals(mimeType, authorizationGrant.mimeType())
                || !Objects.equals(fileSizeBytes, authorizationGrant.fileSizeBytes())
                || !Objects.equals(durationMs, authorizationGrant.durationMs())
                || learningFocus != authorizationGrant.learningFocus()) {
            throw new IllegalArgumentException("authorizationGrant does not bind this request");
        }
    }

    private static void requireEquals(String expected, String value, String field) {
        if (!expected.equals(value)) {
            throw new IllegalArgumentException(field + " is unsupported");
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private static void requireSha256(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lower-case SHA-256 digest");
        }
    }

    private static boolean hasTraversalSegment(String value) {
        for (String segment : value.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
