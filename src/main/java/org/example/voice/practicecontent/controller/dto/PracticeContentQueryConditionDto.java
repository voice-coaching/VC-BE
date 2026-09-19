package org.example.voice.practicecontent.controller.dto;

import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.domain.type.Difficulty;
import org.example.voice.practicecontent.domain.type.LearningFocus;

public record PracticeContentQueryConditionDto(
        ContentType type,
        String category,
        Difficulty difficulty,
        LearningFocus focus,
        Integer page,
        Integer size
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public PracticeContentQueryConditionDto normalized() {
        int normalizedPage = page == null ? DEFAULT_PAGE : Math.max(page, 0);
        int normalizedSize = size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE);
        return new PracticeContentQueryConditionDto(type, category, difficulty, focus, normalizedPage, normalizedSize);
    }
}
