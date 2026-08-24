package org.example.voice.course.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.course.application.CourseService;
import org.example.voice.course.application.CourseStepService;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.course.controller.dto.CourseDetailResponseDto;
import org.example.voice.course.controller.dto.CourseListResponseDto;
import org.example.voice.course.controller.dto.CourseSearchConditionDto;
import org.example.voice.course.controller.dto.CourseStepResponseDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;
    private final CourseStepService courseStepService;

    @GetMapping
    public ApiResponse<CourseListResponseDto> getCourses(
            @AuthenticationPrincipal LoginUser user,
            @ModelAttribute CourseSearchConditionDto condition
    ) {
        CourseListResponseDto response = courseService.getCourses(condition, user.id());
        return ApiResponse.success("클래스 목록을 조회했습니다.", response);
    }

    @GetMapping("/{courseId}")
    public ApiResponse<CourseDetailResponseDto> getCourse(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long courseId
    ) {
        CourseDetailResponseDto response = courseService.getCourse(courseId, user.id());
        return ApiResponse.success("클래스 상세 정보를 조회했습니다.", response);
    }

    @GetMapping("/{courseId}/steps")
    public ApiResponse<CourseStepResponseDto> getCourseSteps(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long courseId
    ) {
        CourseStepResponseDto response = courseStepService.getCourseSteps(courseId, user.id());
        return ApiResponse.success("클래스 단계 목록을 조회했습니다.", response);
    }
}
