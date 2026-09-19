package org.example.voice.training.domain.model;

import org.example.voice.training.domain.type.TrainingSessionStatus;

import java.time.OffsetDateTime;

public record TrainingSessionDetailData(
        Long id,
        TrainingSessionStatus status,
        ContentData content,
        Long selectedRecordingId,
        Integer recordingCount,
        Boolean analysisAvailable,
        OffsetDateTime startedAt
) {

    public record ContentData(
            Long id,
            String title,
            String scriptText,
            String practiceExampleId,
            Integer practiceExampleRevision
    ) {
        public ContentData(Long id, String title, String scriptText) { this(id, title, scriptText, null, null); }
    }
}
