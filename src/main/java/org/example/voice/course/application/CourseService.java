package org.example.voice.course.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.course.controller.dto.CourseListResponseDto;
import org.example.voice.course.controller.dto.CourseSearchConditionDto;
import org.example.voice.course.domain.port.CourseReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseReader courseReader;

    @Transactional(readOnly = true)
    public CourseListResponseDto getCourses(CourseSearchConditionDto condition, Long userId) {
        return CourseListResponseDto.from(courseReader.findCourses(condition.normalized(), userId));
    }
}
