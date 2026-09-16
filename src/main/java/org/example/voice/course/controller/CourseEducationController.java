package org.example.voice.course.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.course.application.CourseEducationService;
import org.example.voice.course.controller.dto.CourseEducationResponseDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class CourseEducationController {
    private final CourseEducationService education;
    @GetMapping("/api/courses/{courseId}/steps/{stepId}")
    public ApiResponse<CourseEducationResponseDto> detail(@AuthenticationPrincipal LoginUser user,
            @PathVariable Long courseId, @PathVariable Long stepId, @RequestParam(required = false) Long sessionId) {
        return ApiResponse.success("클래스 단계 내용을 조회했습니다.", CourseEducationResponseDto.from(education.detail(courseId, stepId, user.id(), sessionId)));
    }
}
