package org.example.voice.training.domain.model;

import java.time.OffsetDateTime;
import java.util.List;

public record TrainingRecoveryData(Long sessionId, String status, Long selectedRecordingId,
                                   AnalysisProgressData analysis, List<Upload> uploads,
                                   boolean hasMoreUploads, OffsetDateTime serverTime) {
    public record Upload(Long uploadIntentId, String objectKey, String mimeType, Long fileSizeBytes,
                         String status, Long recordingId, OffsetDateTime expiresAt) {}
}
