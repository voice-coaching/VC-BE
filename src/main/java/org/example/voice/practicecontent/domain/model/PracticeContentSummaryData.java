package org.example.voice.practicecontent.domain.model;

import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.domain.type.Difficulty;

public record PracticeContentSummaryData(
        Long id,
        ContentType contentType,
        String title,
        String category,
        Difficulty difficulty,
        Integer estimatedSeconds,
        String scriptText
) {
}
