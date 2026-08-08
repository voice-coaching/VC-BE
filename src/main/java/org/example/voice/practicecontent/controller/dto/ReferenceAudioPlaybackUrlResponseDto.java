package org.example.voice.practicecontent.controller.dto;

import org.example.voice.practicecontent.domain.model.ReferenceAudioPlaybackUrlData;

import java.time.OffsetDateTime;

public record ReferenceAudioPlaybackUrlResponseDto(
        Long audioId,
        String playbackUrl,
        OffsetDateTime expiresAt
) {

    public static ReferenceAudioPlaybackUrlResponseDto from(ReferenceAudioPlaybackUrlData data) {
        return new ReferenceAudioPlaybackUrlResponseDto(
                data.audioId(),
                data.playbackUrl(),
                data.expiresAt()
        );
    }
}
