package org.example.voice.training.controller.dto;

public record RecordingRegisterRequestDto(
        String objectKey,
        String mimeType,
        Long fileSizeBytes,
        Integer durationMs
) {
}
