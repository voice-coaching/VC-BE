package org.example.voice.course.controller.dto;

import org.example.voice.course.domain.model.CourseBlock;
import org.example.voice.course.domain.model.CourseEducationData;
import org.example.voice.course.domain.type.CourseStepType;
import java.util.List;

public record CourseEducationResponseDto(Long id, Long courseId, Integer stepOrder, CourseStepType stepType,
        String title, String subtitle, Integer contentRevision, List<CourseBlock> blocks, boolean completed) {
    public static CourseEducationResponseDto from(CourseEducationData data) {
        return new CourseEducationResponseDto(data.id(), data.courseId(), data.stepOrder(), data.stepType(),
                data.title(), data.subtitle(), data.contentRevision(), data.blocks(), data.completed());
    }
}
