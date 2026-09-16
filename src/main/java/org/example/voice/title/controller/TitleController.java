package org.example.voice.title.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.title.application.TitleService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/users/me")
public class TitleController {
    private final TitleService service;
    public record SubmitRequest(@NotNull @Positive Long analysisId) {}
    @GetMapping("/title")
    public ApiResponse<TitleDtos.Progress> progress(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.success("칭호를 조회했습니다.",TitleDtos.Progress.from(service.progress(user.id())));
    }
    @PostMapping("/title-exams") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TitleDtos.Exam> create(@AuthenticationPrincipal LoginUser user,
            @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return ApiResponse.success("승급 시험을 준비했습니다.",TitleDtos.Exam.from(service.create(user.id(),key)));
    }
    @GetMapping("/title-exams/{examId}")
    public ApiResponse<TitleDtos.Exam> get(@AuthenticationPrincipal LoginUser user,@PathVariable Long examId) {
        return ApiResponse.success("승급 시험을 조회했습니다.",TitleDtos.Exam.from(service.get(user.id(),examId)));
    }
    @PostMapping("/title-exams/{examId}/submit")
    public ApiResponse<TitleDtos.Grade> submit(@AuthenticationPrincipal LoginUser user,@PathVariable Long examId,
                                                @Valid @RequestBody SubmitRequest request) {
        return ApiResponse.success("승급 시험 채점이 완료됐습니다.",TitleDtos.Grade.from(service.submit(user.id(),examId,request.analysisId())));
    }
}
