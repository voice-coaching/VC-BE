package org.example.voice.training.domain.model;

import java.util.List;

/** Shared upload limits for admission and the client capability response. */
public final class RecordingMediaPolicy {
    public static final String VIDEO_CONSENT_POLICY_REVISION = "voice-video-processing-consent-v1";
    public static final long MAXIMUM_AUDIO_BYTES = 20L * 1024L * 1024L;
    public static final long MAXIMUM_VIDEO_BYTES = 100L * 1024L * 1024L;
    public static final List<String> AUDIO_MIME_TYPES = List.of(
            "audio/webm", "audio/mpeg", "audio/wav"
    );
    public static final List<String> VIDEO_MIME_TYPES = List.of(
            "video/mp4", "video/quicktime", "video/webm"
    );

    private RecordingMediaPolicy() {
    }
}
