package org.example.voice.course.controller.dto;

import org.example.voice.course.domain.model.CoursePageData;
import org.example.voice.course.domain.model.CourseSummaryData;

import java.util.List;

public record CourseListResponseDto(
        List<CourseListItemDto> items,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages
) {

    public static CourseListResponseDto from(CoursePageData<CourseSummaryData> data) {
        return new CourseListResponseDto(
                data.items().stream()
                        .map(CourseListItemDto::from)
                        .toList(),
                data.page(),
                data.size(),
                data.totalElements(),
                data.totalPages()
        );
    }
}
