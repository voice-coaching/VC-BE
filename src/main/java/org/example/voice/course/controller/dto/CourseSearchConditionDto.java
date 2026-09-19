package org.example.voice.course.controller.dto;

import org.example.voice.course.domain.type.CourseType;
import org.example.voice.practicecontent.domain.type.Difficulty;
import org.example.voice.practicecontent.domain.type.PublishStatus;

public record CourseSearchConditionDto(
        CourseType type,
        Difficulty difficulty,
        PublishStatus status,
        Integer page,
        Integer size
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public CourseSearchConditionDto normalized() {
        int normalizedPage = page == null ? DEFAULT_PAGE : Math.max(page, 0);
        int normalizedSize = size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE);
        PublishStatus normalizedStatus = status == null ? PublishStatus.PUBLISHED : status;
        return new CourseSearchConditionDto(type, difficulty, normalizedStatus, normalizedPage, normalizedSize);
    }
}
