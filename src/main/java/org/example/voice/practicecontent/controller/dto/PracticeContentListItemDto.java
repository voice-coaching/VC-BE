package org.example.voice.practicecontent.controller.dto;

import org.example.voice.practicecontent.domain.model.PracticeContentSummaryData;

public record PracticeContentListItemDto(
        Long id,
        String contentType,
        String title,
        String category,
        String difficulty,
        Integer estimatedSeconds,
        String publisher,
        Integer paragraphCount,
        Integer sentenceCount,
        Integer syllableCount,
        java.time.OffsetDateTime publishedAt,
        String speakerName
) {

    public static PracticeContentListItemDto from(PracticeContentSummaryData data) {
        return new PracticeContentListItemDto(
                data.id(),
                data.contentType().name(),
                data.title(),
                data.category(),
                data.difficulty().name(),
                data.estimatedSeconds(),
                data.publisher(),data.paragraphCount(),data.sentenceCount(),data.syllableCount(),data.publishedAt(),data.speakerName()
        );
    }
}
