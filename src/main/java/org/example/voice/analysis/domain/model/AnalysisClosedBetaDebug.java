package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw media and paths intentionally carried by the closed-beta result. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AnalysisClosedBetaDebug(
        String schemaVersion,
        AnalysisClosedBetaContext context,
        String captureState,
        String audioObjectKey,
        String visualObjectKey,
        String materializedAudioPath,
        String decodedAudioPath,
        String materializedVideoPath,
        String audioMediaBase64,
        String decodedAudioMediaBase64,
        String videoMediaBase64
) {
    public static final String SCHEMA_VERSION = "voice-coaching.closed-beta-debug.v1";
    private static final int MAXIMUM_PATH_LENGTH = 4_096;
    private static final int MAXIMUM_AUDIO_BASE64_LENGTH = 28_000_000;
    private static final int MAXIMUM_DECODED_AUDIO_BASE64_LENGTH = 90_000_000;
    private static final int MAXIMUM_VIDEO_BASE64_LENGTH = 180_000_000;

    public AnalysisClosedBetaDebug {
        if (!SCHEMA_VERSION.equals(schemaVersion) || context == null) {
            throw new IllegalArgumentException("closed beta debug schema is invalid");
        }
        if (!"COMPLETE".equals(captureState)
                && !"PARTIAL".equals(captureState)
                && !"UNAVAILABLE".equals(captureState)) {
            throw new IllegalArgumentException("closed beta capture state is invalid");
        }
        requireText(audioObjectKey, 1_000, "audioObjectKey");
        requireNullableText(visualObjectKey, 1_000, "visualObjectKey");
        requireAbsolutePath(materializedAudioPath, "materializedAudioPath");
        requireAbsolutePath(decodedAudioPath, "decodedAudioPath");
        requireAbsolutePath(materializedVideoPath, "materializedVideoPath");
        requireBase64(audioMediaBase64, MAXIMUM_AUDIO_BASE64_LENGTH, "audioMediaBase64");
        requireBase64(
                decodedAudioMediaBase64,
                MAXIMUM_DECODED_AUDIO_BASE64_LENGTH,
                "decodedAudioMediaBase64"
        );
        requireBase64(videoMediaBase64, MAXIMUM_VIDEO_BASE64_LENGTH, "videoMediaBase64");
        if ("COMPLETE".equals(captureState)
                && (materializedAudioPath == null
                || decodedAudioPath == null
                || audioMediaBase64 == null
                || decodedAudioMediaBase64 == null
                || (visualObjectKey != null
                && (materializedVideoPath == null || videoMediaBase64 == null)))) {
            throw new IllegalArgumentException("complete closed beta capture is incomplete");
        }
        if ("UNAVAILABLE".equals(captureState)
                && (materializedAudioPath != null
                || decodedAudioPath != null
                || materializedVideoPath != null
                || audioMediaBase64 != null
                || decodedAudioMediaBase64 != null
                || videoMediaBase64 != null)) {
            throw new IllegalArgumentException("unavailable closed beta capture contains media");
        }
    }

    private static void requireAbsolutePath(String value, String field) {
        if (value != null && (!value.startsWith("/") || value.length() > MAXIMUM_PATH_LENGTH)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private static void requireText(String value, int maximum, String field) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private static void requireNullableText(String value, int maximum, String field) {
        if (value != null) {
            requireText(value, maximum, field);
        }
    }

    private static void requireBase64(String value, int maximum, String field) {
        if (value == null) {
            return;
        }
        if (value.isEmpty() || value.length() > maximum || value.length() % 4 != 0) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        int padding = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '=') {
                padding++;
                if (index < value.length() - 2 || padding > 2) {
                    throw new IllegalArgumentException(field + " is invalid");
                }
            } else if (padding > 0 || !isBase64Character(character)) {
                throw new IllegalArgumentException(field + " is invalid");
            }
        }
    }

    private static boolean isBase64Character(char value) {
        return value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '+'
                || value == '/';
    }
}
