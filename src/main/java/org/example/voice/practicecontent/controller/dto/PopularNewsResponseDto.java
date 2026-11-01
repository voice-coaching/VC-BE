package org.example.voice.practicecontent.controller.dto;

import java.time.OffsetDateTime;
import java.util.List;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.domain.type.Difficulty;

public record PopularNewsResponseDto(
        ContentType type,
        String rankingBasis,
        OffsetDateTime periodStart,
        OffsetDateTime periodEnd,
        OffsetDateTime generatedAt,
        OffsetDateTime nextUpdateAt,
        List<Item> items
) {
    public record Item(int rank, long usageCount, Long id, ContentType contentType,
                       String title, String category, Difficulty difficulty,
                       Integer estimatedSeconds, String publisher, int paragraphCount,
                       int sentenceCount, OffsetDateTime publishedAt) {}
}
