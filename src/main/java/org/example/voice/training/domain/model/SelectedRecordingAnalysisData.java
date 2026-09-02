package org.example.voice.training.domain.model;

import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.domain.type.RecordingQualityStatus;

/** Trusted database projection used to create one AI analysis request. */
public record SelectedRecordingAnalysisData(
        Long recordingId,
        Long contentId,
        String promptRevision,
        String scriptText,
        String audioObjectKey,
        String mimeType,
        Long fileSizeBytes,
        Integer durationMs,
        LearningFocus learningFocus,
        RecordingQualityStatus qualityStatus
) {
}
