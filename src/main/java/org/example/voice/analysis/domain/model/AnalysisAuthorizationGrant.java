package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.voice.practicecontent.domain.type.LearningFocus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A short-lived, signed, per-request authority to process one recording. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AnalysisAuthorizationGrant(
        String grantVersion,
        String keyId,
        UUID requestEventId,
        Long analysisId,
        Long contentId,
        String promptRevision,
        String scriptSha256,
        String audioObjectKeySha256,
        String mimeType,
        Long fileSizeBytes,
        Integer durationMs,
        LearningFocus learningFocus,
        String consentReceiptSha256,
        String consentPolicyRevision,
        Instant issuedAtUtc,
        Instant expiresAtUtc,
        String purpose,
        String dataCategory,
        boolean deleteOnCompletion,
        boolean remoteEgressAllowed,
        String signature
) {
    public static final String GRANT_VERSION = "voice-coaching.analysis-authorization.v1";
    public static final String PURPOSE = "pronunciation_coaching";
    public static final String DATA_CATEGORY = "learner_voice_recording";

    public AnalysisAuthorizationGrant {
        requireEquals(GRANT_VERSION, grantVersion, "grantVersion");
        requireIdentifier(keyId, "keyId");
        Objects.requireNonNull(requestEventId, "requestEventId");
        requirePositive(analysisId, "analysisId");
        requirePositive(contentId, "contentId");
        requireText(promptRevision, 100, "promptRevision");
        requireSha256(scriptSha256, "scriptSha256");
        requireSha256(audioObjectKeySha256, "audioObjectKeySha256");
        if (mimeType != null && (mimeType.isBlank() || mimeType.length() > 100)) {
            throw new IllegalArgumentException("mimeType is invalid");
        }
        if (fileSizeBytes != null && fileSizeBytes < 0) {
            throw new IllegalArgumentException("fileSizeBytes must not be negative");
        }
        if (durationMs != null && durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        Objects.requireNonNull(learningFocus, "learningFocus");
        requireSha256(consentReceiptSha256, "consentReceiptSha256");
        requireText(consentPolicyRevision, 100, "consentPolicyRevision");
        Objects.requireNonNull(issuedAtUtc, "issuedAtUtc");
        Objects.requireNonNull(expiresAtUtc, "expiresAtUtc");
        if (!expiresAtUtc.isAfter(issuedAtUtc)) {
            throw new IllegalArgumentException("expiresAtUtc must be after issuedAtUtc");
        }
        requireEquals(PURPOSE, purpose, "purpose");
        requireEquals(DATA_CATEGORY, dataCategory, "dataCategory");
        if (!deleteOnCompletion || remoteEgressAllowed) {
            throw new IllegalArgumentException("authorization policy is not permitted");
        }
        requireSha256(signature, "signature");
    }

    /**
     * Cross-language canonical form. Each value is UTF-8 length-prefixed, and
     * null is represented as a negative length. The signature field is omitted.
     */
    public byte[] canonicalSigningInput() {
        StringBuilder value = new StringBuilder(1_024);
        append(value, "grantVersion", grantVersion);
        append(value, "keyId", keyId);
        append(value, "requestEventId", requestEventId);
        append(value, "analysisId", analysisId);
        append(value, "contentId", contentId);
        append(value, "promptRevision", promptRevision);
        append(value, "scriptSha256", scriptSha256);
        append(value, "audioObjectKeySha256", audioObjectKeySha256);
        append(value, "mimeType", mimeType);
        append(value, "fileSizeBytes", fileSizeBytes);
        append(value, "durationMs", durationMs);
        append(value, "learningFocus", learningFocus);
        append(value, "consentReceiptSha256", consentReceiptSha256);
        append(value, "consentPolicyRevision", consentPolicyRevision);
        append(value, "issuedAtUtc", issuedAtUtc);
        append(value, "expiresAtUtc", expiresAtUtc);
        append(value, "purpose", purpose);
        append(value, "dataCategory", dataCategory);
        append(value, "deleteOnCompletion", deleteOnCompletion);
        append(value, "remoteEgressAllowed", remoteEgressAllowed);
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void append(StringBuilder target, String name, Object rawValue) {
        if (rawValue == null) {
            target.append(name).append(":-1:\n");
            return;
        }
        String value = rawValue instanceof LearningFocus focus ? focus.name() : rawValue.toString();
        int byteLength = value.getBytes(StandardCharsets.UTF_8).length;
        target.append(name).append(':').append(byteLength).append(':').append(value).append('\n');
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

    private static void requireIdentifier(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,100}")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private static void requireSha256(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lower-case SHA-256 digest");
        }
    }
}
