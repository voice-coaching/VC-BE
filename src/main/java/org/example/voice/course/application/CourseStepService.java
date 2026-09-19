package org.example.voice.course.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.course.controller.dto.CourseStepResponseDto;
import org.example.voice.course.domain.port.CourseStepReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseStepService {

    private final CourseStepReader courseStepReader;

    @Transactional(readOnly = true)
    public CourseStepResponseDto getCourseSteps(Long courseId, Long userId) {
        return courseStepReader.findCourseSteps(courseId, userId)
                .map(CourseStepResponseDto::from)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
