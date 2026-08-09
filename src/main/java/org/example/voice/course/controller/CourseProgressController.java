package org.example.voice.course.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.course.application.CourseProgressService;
import org.example.voice.course.controller.dto.CourseCompleteResponseDto;
import org.example.voice.course.controller.dto.CourseProgressDetailResponseDto;
import org.example.voice.course.controller.dto.CourseProgressResponseDto;
import org.example.voice.course.controller.dto.CourseProgressUpdateRequestDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/{courseId}/progress")
    public ApiResponse<CourseProgressDetailResponseDto> getCourseProgress(@PathVariable Long courseId) {
        Long userId = 1L;
        CourseProgressDetailResponseDto response = courseProgressService.getCourseProgress(courseId, userId);
        return ApiResponse.success("클래스 진행 상태를 조회했습니다.", response);
    }

    @PatchMapping("/{courseId}/progress")
    public ApiResponse<CourseProgressResponseDto> updateCourseProgress(
            @PathVariable Long courseId,
            @RequestBody CourseProgressUpdateRequestDto request
    ) {
        Long userId = 1L;
        CourseProgressResponseDto response = courseProgressService.updateCourseProgress(courseId, userId, request);
        return ApiResponse.success("클래스 진행 상태가 저장되었습니다.", response);
    }

    @PostMapping("/{courseId}/complete")
    public ApiResponse<CourseCompleteResponseDto> completeCourse(@PathVariable Long courseId) {
        Long userId = 1L;
        CourseCompleteResponseDto response = courseProgressService.completeCourse(courseId, userId);
        return ApiResponse.success("클래스를 완료했습니다.", response);
    }
}
