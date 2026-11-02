package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw media and paths intentionally carried by the closed-beta result. */
@JsonIgnoreProperties(ignoreUnknown = true)
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

    }

    private static boolean isBase64Character(char value) {
        return value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '+'
                || value == '/';
    }
}
