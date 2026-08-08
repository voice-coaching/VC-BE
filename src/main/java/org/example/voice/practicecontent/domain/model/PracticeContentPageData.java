package org.example.voice.practicecontent.domain.model;

import java.util.List;

public record PracticeContentPageData<T>(
        List<T> items,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages,
        Boolean hasNext
) {

    public static <T> PracticeContentPageData<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PracticeContentPageData<>(
                items,
                page,
                size,
                totalElements,
                totalPages,
                page + 1 < totalPages
        );
    }
}
