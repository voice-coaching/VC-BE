package org.example.voice.training.controller.dto;

import org.example.voice.training.domain.model.RecordingUploadUrlData;

import java.time.OffsetDateTime;
import java.util.Map;

public record RecordingUploadUrlResponseDto(
        String objectKey,
        String uploadUrl,
        OffsetDateTime expiresAt,
        Map<String, String> requiredHeaders
) {

    public static RecordingUploadUrlResponseDto from(RecordingUploadUrlData data) {
        return new RecordingUploadUrlResponseDto(
                data.objectKey(),
                data.uploadUrl(),
                data.expiresAt(),
                data.requiredHeaders()
        );
    }
}
