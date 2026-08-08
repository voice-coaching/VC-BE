package org.example.voice.practicecontent.controller.dto;

import org.example.voice.practicecontent.domain.model.PracticeContentSummaryData;

public record PracticeContentListItemDto(
        Long id,
        String contentType,
        String title,
        String category,
        String difficulty,
        Integer estimatedSeconds
) {

    public static PracticeContentListItemDto from(PracticeContentSummaryData data) {
        return new PracticeContentListItemDto(
                data.id(),
                data.contentType().name(),
                data.title(),
                data.category(),
                data.difficulty().name(),
                data.estimatedSeconds()
        );
    }
}
