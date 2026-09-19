package org.example.voice.course.domain.model;

import java.util.List;

public record CoursePageData<T>(
        List<T> items,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages
) {

    public static <T> CoursePageData<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new CoursePageData<>(items, page, size, totalElements, totalPages);
    }
}
