package org.example.voice.course.domain.port;

import org.example.voice.course.domain.model.CourseStepListData;

import java.util.Optional;

public interface CourseStepReader {

    Optional<CourseStepListData> findCourseSteps(Long courseId, Long userId);
}
