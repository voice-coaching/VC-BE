package org.example.voice.course.domain.port;

import org.example.voice.course.controller.dto.CourseSearchConditionDto;
import org.example.voice.course.domain.model.CourseDetailData;
import org.example.voice.course.domain.model.CoursePageData;
import org.example.voice.course.domain.model.CourseSummaryData;

import java.util.Optional;

public interface CourseReader {

    CoursePageData<CourseSummaryData> findCourses(CourseSearchConditionDto condition, Long userId);

    Optional<CourseDetailData> findCourse(Long courseId, Long userId);
}
