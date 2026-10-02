package org.example.voice.title.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.title.application.TitleService;
import org.example.voice.title.controller.dto.TitleExamGradeResponseDto;
import org.example.voice.title.controller.dto.TitleExamResponseDto;
import org.example.voice.title.controller.dto.TitleExamSubmitRequestDto;
import org.example.voice.title.controller.dto.TitleProgressResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me")
public class TitleController {
    private final TitleService service;

    @GetMapping("/title")
    public ApiResponse<TitleProgressResponseDto> progress(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.success("칭호를 조회했습니다.", TitleProgressResponseDto.from(service.progress(user.id())));
    }

    @PostMapping("/title-exams")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TitleExamResponseDto> create(
            @AuthenticationPrincipal LoginUser user,
            @RequestHeader(value = "Idempotency-Key", required = false) String key
    ) {
        return ApiResponse.success("승급 시험을 준비했습니다.", TitleExamResponseDto.from(service.create(user.id(), key)));
    }

    @GetMapping("/title-exams/{examId}")
    public ApiResponse<TitleExamResponseDto> get(@AuthenticationPrincipal LoginUser user, @PathVariable Long examId) {
        return ApiResponse.success("승급 시험을 조회했습니다.", TitleExamResponseDto.from(service.get(user.id(), examId)));
    }

    @PostMapping("/title-exams/{examId}/submit")
    public ApiResponse<TitleExamGradeResponseDto> submit(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long examId,
            @Valid @RequestBody TitleExamSubmitRequestDto request
    ) {
        return ApiResponse.success(
                "승급 시험 채점이 완료됐습니다.",
                TitleExamGradeResponseDto.from(service.submit(user.id(), examId, request.analysisId()))
        );
    }
}
