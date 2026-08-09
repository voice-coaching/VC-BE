package org.example.voice.training.controller.dto;

import org.example.voice.training.domain.model.RecordingPlaybackUrlData;

import java.time.OffsetDateTime;

public record RecordingPlaybackUrlResponseDto(
        Long recordingId,
        String playbackUrl,
        OffsetDateTime expiresAt
) {

    public static RecordingPlaybackUrlResponseDto from(RecordingPlaybackUrlData data) {
        return new RecordingPlaybackUrlResponseDto(data.recordingId(), data.playbackUrl(), data.expiresAt());
    }
}
