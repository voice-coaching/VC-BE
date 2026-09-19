package org.example.voice.practicecontent.domain.model;

import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.domain.type.Difficulty;
import org.example.voice.practicecontent.domain.type.LearningFocus;

import java.util.List;

public record PracticeContentDetailData(
        Long id,
        ContentType contentType,
        LearningFocus learningFocus,
        String category,
        String title,
        String description,
        String scriptText,
        Difficulty difficulty,
        List<String> targetPronunciations,
        Integer estimatedSeconds,
        Boolean referenceAudioAvailable
) {
}
