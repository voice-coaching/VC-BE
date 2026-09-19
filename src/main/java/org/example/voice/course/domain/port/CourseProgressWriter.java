package org.example.voice.course.domain.port;

import org.example.voice.course.controller.dto.CourseProgressUpdateRequestDto;
import org.example.voice.course.domain.model.CourseProgressData;

public interface CourseProgressWriter {

    CourseProgressData startCourse(Long courseId, Long userId);

    CourseProgressData updateCourseProgress(Long courseId, Long userId, CourseProgressUpdateRequestDto request);

    CourseProgressData completeCourse(Long courseId, Long userId);
}
