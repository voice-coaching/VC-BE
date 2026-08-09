package org.example.voice.course.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.course.controller.dto.CourseProgressResponseDto;
import org.example.voice.course.domain.port.CourseProgressReader;
import org.example.voice.course.domain.port.CourseProgressWriter;
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
}
