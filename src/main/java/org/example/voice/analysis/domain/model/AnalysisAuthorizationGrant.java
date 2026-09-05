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
        String audioSha256,
        String mimeType,
        Long fileSizeBytes,
        Integer durationMs,
        LearningFocus learningFocus,
        String consentReceiptSha256,
        String consentPolicyRevision,
        String visualObjectKeySha256,
        String visualSha256,
        String visualMimeType,
        Long visualFileSizeBytes,
        String visualConsentReceiptSha256,
        String visualConsentPolicyRevision,
        String closedBetaContextSha256,
        Instant issuedAtUtc,
        Instant expiresAtUtc,
        String purpose,
        String dataCategory,
        boolean deleteOnCompletion,
        boolean remoteEgressAllowed,
        String signature
) {
    public static final String GRANT_VERSION = "voice-coaching.analysis-authorization.v4";
    public static final String LEGACY_GRANT_VERSION = "voice-coaching.analysis-authorization.v3";
    public static final String PURPOSE = "pronunciation_coaching";
    public static final String DATA_CATEGORY = "learner_voice_recording";

    public AnalysisAuthorizationGrant(
            String grantVersion,
            String keyId,
            UUID requestEventId,
            Long analysisId,
            Long contentId,
            String promptRevision,
            String scriptSha256,
            String audioObjectKeySha256,
            String audioSha256,
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
        this(grantVersion, keyId, requestEventId, analysisId, contentId,
                promptRevision, scriptSha256, audioObjectKeySha256, audioSha256,
                mimeType, fileSizeBytes, durationMs, learningFocus,
                consentReceiptSha256, consentPolicyRevision,
                null, null, null, null, null, null, null,
                issuedAtUtc, expiresAtUtc, purpose, dataCategory,
                deleteOnCompletion, remoteEgressAllowed, signature);
    }

    public AnalysisAuthorizationGrant {
        if (!GRANT_VERSION.equals(grantVersion) && !LEGACY_GRANT_VERSION.equals(grantVersion)) {
            throw new IllegalArgumentException("grantVersion is unsupported");
        }
        requireIdentifier(keyId, "keyId");
        Objects.requireNonNull(requestEventId, "requestEventId");
        requirePositive(analysisId, "analysisId");
        requirePositive(contentId, "contentId");
        requireText(promptRevision, 100, "promptRevision");
        requireSha256(scriptSha256, "scriptSha256");
        requireSha256(audioObjectKeySha256, "audioObjectKeySha256");
        requireSha256(audioSha256, "audioSha256");
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
        requireSha256(consentReceiptSha256, "consentReceiptSha256");
        requireText(consentPolicyRevision, 100, "consentPolicyRevision");
        validateVisualClaims(
                visualObjectKeySha256,
                visualSha256,
                visualMimeType,
                visualFileSizeBytes,
                visualConsentReceiptSha256,
                visualConsentPolicyRevision
        );
        if (GRANT_VERSION.equals(grantVersion)) {
            requireSha256(closedBetaContextSha256, "closedBetaContextSha256");
        } else if (closedBetaContextSha256 != null) {
            throw new IllegalArgumentException("legacy grant must not bind closed beta context");
        }
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
        append(value, "audioSha256", audioSha256);
        append(value, "mimeType", mimeType);
        append(value, "fileSizeBytes", fileSizeBytes);
        append(value, "durationMs", durationMs);
        append(value, "learningFocus", learningFocus);
        append(value, "consentReceiptSha256", consentReceiptSha256);
        append(value, "consentPolicyRevision", consentPolicyRevision);
        append(value, "visualObjectKeySha256", visualObjectKeySha256);
        append(value, "visualSha256", visualSha256);
        append(value, "visualMimeType", visualMimeType);
        append(value, "visualFileSizeBytes", visualFileSizeBytes);
        append(value, "visualConsentReceiptSha256", visualConsentReceiptSha256);
        append(value, "visualConsentPolicyRevision", visualConsentPolicyRevision);
        if (GRANT_VERSION.equals(grantVersion)) {
            append(value, "closedBetaContextSha256", closedBetaContextSha256);
        }
        append(value, "issuedAtUtc", issuedAtUtc);
        append(value, "expiresAtUtc", expiresAtUtc);
        append(value, "purpose", purpose);
        append(value, "dataCategory", dataCategory);
        append(value, "deleteOnCompletion", deleteOnCompletion);
        append(value, "remoteEgressAllowed", remoteEgressAllowed);
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    public boolean binds(AnalysisWorkerVisualInput visualInput) {
        if (visualInput == null) {
            return visualObjectKeySha256 == null;
        }
        return sha256(visualInput.objectKey()).equals(visualObjectKeySha256)
                && visualInput.sha256().equals(visualSha256)
                && visualInput.mimeType().equals(visualMimeType)
                && visualInput.fileSizeBytes().equals(visualFileSizeBytes)
                && visualInput.consentReceiptSha256().equals(visualConsentReceiptSha256)
                && visualInput.consentPolicyRevision().equals(visualConsentPolicyRevision);
    }

    public boolean binds(AnalysisClosedBetaContext context) {
        if (context == null) {
            return LEGACY_GRANT_VERSION.equals(grantVersion)
                    && closedBetaContextSha256 == null;
        }
        return GRANT_VERSION.equals(grantVersion)
                && context.bindingSha256().equals(closedBetaContextSha256);
    }

    private static void validateVisualClaims(
            String objectKeySha256,
            String visualSha256,
            String mimeType,
            Long fileSizeBytes,
            String consentReceiptSha256,
            String consentPolicyRevision
    ) {
        boolean absent = objectKeySha256 == null && visualSha256 == null && mimeType == null
                && fileSizeBytes == null && consentReceiptSha256 == null && consentPolicyRevision == null;
        if (absent) {
            return;
        }
        requireSha256(objectKeySha256, "visualObjectKeySha256");
        requireSha256(visualSha256, "visualSha256");
        if (!"video/mp4".equals(mimeType) || fileSizeBytes == null || fileSizeBytes <= 0) {
            throw new IllegalArgumentException("visual media claims are invalid");
        }
        requireSha256(consentReceiptSha256, "visualConsentReceiptSha256");
        requireText(consentPolicyRevision, 100, "visualConsentPolicyRevision");
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
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
