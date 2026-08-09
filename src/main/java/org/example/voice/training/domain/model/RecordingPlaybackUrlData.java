package org.example.voice.training.domain.model;

import java.time.OffsetDateTime;

public record RecordingPlaybackUrlData(
        Long recordingId,
        String playbackUrl,
        OffsetDateTime expiresAt
) {
}
