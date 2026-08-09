package org.example.voice.course.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.course.application.CourseService;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.course.controller.dto.CourseListResponseDto;
import org.example.voice.course.controller.dto.CourseSearchConditionDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public ApiResponse<CourseListResponseDto> getCourses(
            @ModelAttribute CourseSearchConditionDto condition
    ) {
        Long userId = 1L;
        CourseListResponseDto response = courseService.getCourses(condition, userId);
        return ApiResponse.success("클래스 목록을 조회했습니다.", response);
    }
}
