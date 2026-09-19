package org.example.voice.practicecontent.controller.dto;

import org.example.voice.practicecontent.domain.model.PracticeContentPageData;
import org.example.voice.practicecontent.domain.model.PracticeContentSummaryData;

import java.util.List;

public record PracticeContentListResponseDto(
        List<PracticeContentListItemDto> items,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages,
        Boolean hasNext
) {

    public static PracticeContentListResponseDto from(PracticeContentPageData<PracticeContentSummaryData> data) {
        return new PracticeContentListResponseDto(
                data.items().stream()
                        .map(PracticeContentListItemDto::from)
                        .toList(),
                data.page(),
                data.size(),
                data.totalElements(),
                data.totalPages(),
                data.hasNext()
        );
    }
}
