package org.example.voice.training.domain.model;

import java.time.OffsetDateTime;
import org.example.voice.training.domain.type.RecordingUploadIntentStatus;

public record RecordingUploadIntentData(Long id, String objectKey, String mimeType, Long fileSizeBytes,
                                       OffsetDateTime expiresAt, RecordingUploadIntentStatus status, Long recordingId) {}
