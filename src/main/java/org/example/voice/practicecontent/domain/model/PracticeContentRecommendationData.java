package org.example.voice.practicecontent.domain.model;

import org.example.voice.practicecontent.domain.type.ContentType;

public record PracticeContentRecommendationData(
        Long id,
        String title,
        ContentType contentType,
        String similarityReason
) {
}
