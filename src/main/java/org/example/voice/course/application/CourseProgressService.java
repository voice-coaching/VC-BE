package org.example.voice.course.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.course.controller.dto.CourseCompleteResponseDto;
import org.example.voice.course.controller.dto.CourseProgressDetailResponseDto;
import org.example.voice.course.controller.dto.CourseProgressListResponseDto;
import org.example.voice.course.controller.dto.CourseProgressResponseDto;
import org.example.voice.course.controller.dto.CourseProgressSearchConditionDto;
import org.example.voice.course.controller.dto.CourseProgressUpdateRequestDto;
import org.example.voice.course.domain.port.CourseProgressReader;
import org.example.voice.course.domain.port.CourseProgressWriter;
import org.example.voice.course.exception.CourseProgressNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseProgressService {

    private final CourseProgressReader courseProgressReader;
    private final CourseProgressWriter courseProgressWriter;

    @Transactional
    public CourseProgressResponseDto startCourse(Long courseId, Long userId) {
        return CourseProgressResponseDto.from(courseProgressWriter.startCourse(courseId, userId));
    }

    @Transactional(readOnly = true)
    public CourseProgressListResponseDto getMyCourseProgress(Long userId, CourseProgressSearchConditionDto condition) {
        return CourseProgressListResponseDto.from(courseProgressReader.findMyCourseProgress(userId, condition));
    }

    @Transactional(readOnly = true)
    public CourseProgressDetailResponseDto getCourseProgress(Long courseId, Long userId) {
        return courseProgressReader.findCourseProgress(courseId, userId)
                .map(CourseProgressDetailResponseDto::from)
                .orElseThrow(CourseProgressNotFoundException::new);
    }

    @Transactional
    public CourseProgressResponseDto updateCourseProgress(Long courseId, Long userId, CourseProgressUpdateRequestDto request) {
        return CourseProgressResponseDto.from(courseProgressWriter.updateCourseProgress(courseId, userId, request));
    }

    @Transactional
    public CourseCompleteResponseDto completeCourse(Long courseId, Long userId) {
        return CourseCompleteResponseDto.from(courseProgressWriter.completeCourse(courseId, userId));
    }
}
