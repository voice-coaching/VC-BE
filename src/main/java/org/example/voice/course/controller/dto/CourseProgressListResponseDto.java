package org.example.voice.course.controller.dto;

import org.example.voice.course.domain.model.CourseProgressListData;

import java.util.List;

public record CourseProgressListResponseDto(
        List<CourseProgressListItemDto> items
) {

    public static CourseProgressListResponseDto from(CourseProgressListData data) {
        return new CourseProgressListResponseDto(
                data.items().stream()
                        .map(CourseProgressListItemDto::from)
                        .toList()
        );
    }
}
