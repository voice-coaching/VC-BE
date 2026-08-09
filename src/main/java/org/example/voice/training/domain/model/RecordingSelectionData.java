package org.example.voice.training.domain.model;

import java.time.OffsetDateTime;

public record RecordingSelectionData(
        Long sessionId,
        Long selectedRecordingId,
        OffsetDateTime selectedAt
) {
}
