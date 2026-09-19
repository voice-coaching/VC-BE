package org.example.voice.practicecontent.domain.model;

import java.time.OffsetDateTime;

public record ReferenceAudioPlaybackUrlData(
        Long audioId,
        String playbackUrl,
        OffsetDateTime expiresAt
) {
}
