package org.example.voice.home.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.home.application.RecommendationService;
import org.example.voice.home.controller.dto.RecommendationResponseDto;
import org.example.voice.home.controller.dto.RecommendationSearchConditionDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    public ApiResponse<RecommendationResponseDto> getRecommendations(
            @ModelAttribute RecommendationSearchConditionDto condition
    ) {
        RecommendationResponseDto response = recommendationService.getRecommendations(condition);
        return ApiResponse.success("추천 콘텐츠를 조회했습니다.", response);
    }
}
