package org.example.voice.course.domain.port;

import org.example.voice.course.controller.dto.CourseSearchConditionDto;
import org.example.voice.course.domain.model.CoursePageData;
import org.example.voice.course.domain.model.CourseSummaryData;

public interface CourseReader {

    CoursePageData<CourseSummaryData> findCourses(CourseSearchConditionDto condition, Long userId);
}
