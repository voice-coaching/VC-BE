package org.example.voice.training.domain.model;

import java.util.List;

/** Shared upload limits for admission and the client capability response. */
public final class RecordingMediaPolicy {
    public static final String VIDEO_CONSENT_POLICY_REVISION = "voice-video-processing-consent-v1";
    public static final long MAXIMUM_AUDIO_BYTES = Long.MAX_VALUE;
    public static final long MAXIMUM_VIDEO_BYTES = Long.MAX_VALUE;
    public static final List<String> AUDIO_MIME_TYPES = List.of(
            "audio/webm", "audio/mpeg", "audio/wav"
    );
    public static final List<String> VIDEO_MIME_TYPES = List.of(
            "video/mp4", "video/quicktime", "video/webm"
    );

    private RecordingMediaPolicy() {
    }
}
