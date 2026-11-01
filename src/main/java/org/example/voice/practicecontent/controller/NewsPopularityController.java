package org.example.voice.practicecontent.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.practicecontent.application.NewsPopularityService;
import org.example.voice.practicecontent.controller.dto.PopularNewsResponseDto;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/practice-contents")
public class NewsPopularityController {
    private final NewsPopularityService service;

    @GetMapping("/popular")
    public ApiResponse<PopularNewsResponseDto> popular(
            @RequestParam(defaultValue="NEWS") ContentType type,
            @RequestParam(defaultValue="10") int limit) {
        return ApiResponse.success("인기 뉴스를 조회했습니다.", service.get(type, limit));
    }
}
