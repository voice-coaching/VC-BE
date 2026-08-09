package org.example.voice.training.controller.dto;

import org.example.voice.training.domain.model.TrainingSessionDetailData;

import java.time.OffsetDateTime;

public record TrainingSessionDetailResponseDto(
        Long id,
        String status,
        TrainingSessionContentDto content,
        Long selectedRecordingId,
        Integer recordingCount,
        Boolean analysisAvailable,
        OffsetDateTime startedAt
) {

    public static TrainingSessionDetailResponseDto from(TrainingSessionDetailData data) {
        return new TrainingSessionDetailResponseDto(
                data.id(),
                data.status().name(),
                TrainingSessionContentDto.from(data.content()),
                data.selectedRecordingId(),
                data.recordingCount(),
                data.analysisAvailable(),
                data.startedAt()
        );
    }
}
