package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.voice.practicecontent.domain.type.LearningFocus;

import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Versioned Backend-to-AI request payload. Production uses v4 without personal
 * context. The explicitly enabled v5 closed-beta contract carries that context.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
        @JsonInclude(JsonInclude.Include.NON_NULL) AnalysisClosedBetaContext closedBetaContext,
        AnalysisWorkerVisualInput visualInput,
        AnalysisAuthorizationGrant authorizationGrant
) {
    public static final String SCHEMA_VERSION = "voice-coaching.analysis-request.v5";
    public static final String LEGACY_SCHEMA_VERSION = "voice-coaching.analysis-request.v4";

    public AnalysisWorkerRequest(
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
        this(schemaVersion, eventId, analysisId, contentId, promptRevision,
                scriptText, scriptSha256, audioObjectKey, audioSha256, mimeType,
                fileSizeBytes, durationMs, learningFocus, null, null,
                authorizationGrant);
    }

    public AnalysisWorkerRequest {

        Objects.requireNonNull(eventId, "eventId");

        Objects.requireNonNull(learningFocus, "learningFocus");

    }

    private static boolean hasTraversalSegment(String value) {
        for (String segment : value.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOwnerSegment(String value) {
        return java.util.Arrays.stream(value.split("/"))
                .anyMatch(segment -> segment.equalsIgnoreCase("users")
                        || segment.equalsIgnoreCase("sessions"));
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
