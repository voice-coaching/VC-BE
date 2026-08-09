package org.example.voice.training.controller.dto;

public record RecordingUploadUrlRequestDto(
        String fileName,
        String mimeType,
        Long fileSizeBytes
) {
}
