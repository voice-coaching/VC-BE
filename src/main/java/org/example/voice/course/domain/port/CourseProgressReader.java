package org.example.voice.course.domain.port;

import org.example.voice.course.controller.dto.CourseProgressSearchConditionDto;
import org.example.voice.course.domain.model.CourseProgressData;
import org.example.voice.course.domain.model.CourseProgressListData;

import java.util.Optional;

public interface CourseProgressReader {

    CourseProgressListData findMyCourseProgress(Long userId, CourseProgressSearchConditionDto condition);

    Optional<CourseProgressData> findCourseProgress(Long courseId, Long userId);
}
