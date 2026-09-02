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
        String audioSha256,
        String visualObjectKey,
        String visualMimeType,
        Long visualFileSizeBytes,
        String visualSha256,
        String visualConsentReceiptSha256,
        String visualConsentPolicyRevision,
        LearningFocus learningFocus,
        RecordingQualityStatus qualityStatus
) {
    public SelectedRecordingAnalysisData(
            Long recordingId,
            Long contentId,
            String promptRevision,
            String scriptText,
            String audioObjectKey,
            String mimeType,
            Long fileSizeBytes,
            Integer durationMs,
            String audioSha256,
            LearningFocus learningFocus,
            RecordingQualityStatus qualityStatus
    ) {
        this(recordingId, contentId, promptRevision, scriptText, audioObjectKey,
                mimeType, fileSizeBytes, durationMs, audioSha256,
                null, null, null, null, null, null, learningFocus, qualityStatus);
    }
}
