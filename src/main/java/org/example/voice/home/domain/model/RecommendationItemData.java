package org.example.voice.home.domain.model;

import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.domain.type.Difficulty;

public record RecommendationItemData(
        Long contentId,
        ContentType contentType,
        String title,
        Difficulty difficulty,
        String reason
) {
}
