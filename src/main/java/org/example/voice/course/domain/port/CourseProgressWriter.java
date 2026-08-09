package org.example.voice.course.domain.port;

import org.example.voice.course.domain.model.CourseProgressData;

public interface CourseProgressWriter {

    CourseProgressData startCourse(Long courseId, Long userId);
}
