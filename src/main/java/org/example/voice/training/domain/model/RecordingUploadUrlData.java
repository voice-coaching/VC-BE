package org.example.voice.training.domain.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record RecordingUploadUrlData(
        String objectKey,
        String uploadUrl,
        OffsetDateTime expiresAt,
        Map<String, String> requiredHeaders
) {
}
