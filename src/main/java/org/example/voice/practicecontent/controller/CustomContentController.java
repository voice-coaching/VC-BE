package org.example.voice.practicecontent.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.practicecontent.application.CustomContentService;
import org.example.voice.practicecontent.controller.dto.PracticeContentDetailResponseDto;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class CustomContentController {
    private final CustomContentService contents;
    public record Request(String title,String scriptText,LearningFocus learningFocus,String retention,String locale) {}
    @PostMapping("/api/practice-contents/custom")
    public ResponseEntity<ApiResponse<PracticeContentDetailResponseDto>> create(@AuthenticationPrincipal LoginUser user,
            @RequestBody Request request,@RequestHeader(value="Idempotency-Key",required=false) String key) {
        var data=contents.create(user.id(),new CustomContentService.Input(request.title(),request.scriptText(),request.learningFocus(),request.retention(),request.locale()),key);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("사용자 문장을 만들었습니다.",PracticeContentDetailResponseDto.fromCustom(data)));
    }
}
