package org.example.voice.training.controller.dto;

import java.time.OffsetDateTime;
import java.util.List;
import org.example.voice.training.domain.model.TrainingRecoveryData;

public record TrainingRecoveryResponseDto(Long sessionId, String status, Long selectedRecordingId,
                                          AnalysisProgressResponseDto analysis, List<Upload> uploads,
                                          boolean hasMoreUploads, OffsetDateTime serverTime) {
    public record Upload(Long uploadIntentId, String objectKey, String mimeType, Long fileSizeBytes,
                         String status, Long recordingId, OffsetDateTime expiresAt) {}

    public static TrainingRecoveryResponseDto from(TrainingRecoveryData d) {
        return new TrainingRecoveryResponseDto(d.sessionId(), d.status(), d.selectedRecordingId(),
                d.analysis() == null ? null : AnalysisProgressResponseDto.from(d.analysis()),
                d.uploads().stream().map(u -> new Upload(u.uploadIntentId(), u.objectKey(), u.mimeType(),
                        u.fileSizeBytes(), u.status(), u.recordingId(), u.expiresAt())).toList(),
                d.hasMoreUploads(), d.serverTime());
    }
}
