package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Personal identifiers intentionally transported during the closed beta. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AnalysisClosedBetaContext(
        String schemaVersion,
        Long userId,
        Long sessionId,
        Long recordingId
) {
    public static final String SCHEMA_VERSION = "voice-coaching.closed-beta-context.v1";

    public AnalysisClosedBetaContext {
        if (!SCHEMA_VERSION.equals(schemaVersion)
                || userId == null || userId <= 0
                || sessionId == null || sessionId <= 0
                || recordingId == null || recordingId <= 0) {
            throw new IllegalArgumentException("closed beta context is invalid");
        }
    }

    public byte[] canonicalBindingInput() {
        return ("schemaVersion:" + SCHEMA_VERSION + "\n"
                + "userId:" + userId + "\n"
                + "sessionId:" + sessionId + "\n"
                + "recordingId:" + recordingId + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public String bindingSha256() {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonicalBindingInput())
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
