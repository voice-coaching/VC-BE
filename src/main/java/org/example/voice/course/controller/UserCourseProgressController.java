package org.example.voice.course.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.course.application.CourseProgressService;
import org.example.voice.course.controller.dto.CourseProgressListResponseDto;
import org.example.voice.course.controller.dto.CourseProgressSearchConditionDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/course-progress")
public class UserCourseProgressController {

    private final CourseProgressService courseProgressService;

    @GetMapping
    public ApiResponse<CourseProgressListResponseDto> getMyCourseProgress(
            @ModelAttribute CourseProgressSearchConditionDto condition
    ) {
        Long userId = 1L;
        CourseProgressListResponseDto response = courseProgressService.getMyCourseProgress(userId, condition);
        return ApiResponse.success("클래스 진행 목록을 조회했습니다.", response);
    }
}
