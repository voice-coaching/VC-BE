package org.example.voice.training.controller.dto;

import org.example.voice.training.domain.model.TrainingSessionDetailData;

public record TrainingSessionContentDto(
        Long id,
        String title,
        String scriptText,
        String practiceExampleId,
        Integer practiceExampleRevision,
        Integer sentenceIndex
) {

    public static TrainingSessionContentDto from(TrainingSessionDetailData.ContentData data) {
        return new TrainingSessionContentDto(data.id(), data.title(), data.scriptText(), data.practiceExampleId(), data.practiceExampleRevision(), data.sentenceIndex());
    }
}
