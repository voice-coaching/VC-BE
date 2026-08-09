package org.example.voice.course.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.course.application.CourseProgressService;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.course.controller.dto.CourseProgressResponseDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/courses")
public class CourseProgressController {

    private final CourseProgressService courseProgressService;

    @PostMapping("/{courseId}/start")
    public ApiResponse<CourseProgressResponseDto> startCourse(@PathVariable Long courseId) {
        Long userId = 1L;
        CourseProgressResponseDto response = courseProgressService.startCourse(courseId, userId);
        return ApiResponse.success("클래스 학습을 시작했습니다.", response);
    }
}
