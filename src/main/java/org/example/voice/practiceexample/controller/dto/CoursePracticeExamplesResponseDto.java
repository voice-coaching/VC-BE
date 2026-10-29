package org.example.voice.practiceexample.controller.dto;

import java.util.List;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.practiceexample.domain.model.ExampleData.Examples;

public record CoursePracticeExamplesResponseDto(Long courseId, Long stepId, int revision,
                                                List<PracticeExampleItemDto> items) {
    public static CoursePracticeExamplesResponseDto from(Examples data) {
        return new CoursePracticeExamplesResponseDto(data.courseId(), data.stepId(), data.revision(),
                data.items().stream().map(item -> new PracticeExampleItemDto(item.id(), item.order(), item.text(),
                        item.hint(), item.focus(), item.locale(), item.practiceContentId())).toList());
    }

    public record PracticeExampleItemDto(String id, int order, String text, String hint, LearningFocus focus,
                                         String locale, Long practiceContentId) {}
}
