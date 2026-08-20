package org.example.voice.practicecontent.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.practicecontent.application.PracticeContentService;
import org.example.voice.practicecontent.controller.dto.PracticeContentDetailResponseDto;
import org.example.voice.practicecontent.controller.dto.PracticeContentListResponseDto;
import org.example.voice.practicecontent.controller.dto.PracticeContentNextConditionDto;
import org.example.voice.practicecontent.controller.dto.PracticeContentQueryConditionDto;
import org.example.voice.practicecontent.controller.dto.PracticeContentRecommendationResponseDto;
import org.example.voice.practicecontent.controller.dto.PracticeContentRecommendationsResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/practice-contents")
public class PracticeContentController {

    private final PracticeContentService practiceContentService;

    @GetMapping
    public ApiResponse<PracticeContentListResponseDto> getPracticeContents(
            @ModelAttribute PracticeContentQueryConditionDto condition
    ) {
        PracticeContentListResponseDto response = practiceContentService.getPracticeContents(condition);
        return ApiResponse.success("학습 콘텐츠 목록을 조회했습니다.", response);
    }

    @GetMapping("/next")
    public ApiResponse<PracticeContentRecommendationResponseDto> getNextPracticeContent(
            @ModelAttribute PracticeContentNextConditionDto condition
    ) {
        PracticeContentRecommendationResponseDto response = practiceContentService.getNextPracticeContent(condition);
        return ApiResponse.success("다음 학습 콘텐츠를 조회했습니다.", response);
    }

    @GetMapping("/{contentId}")
    public ApiResponse<PracticeContentDetailResponseDto> getPracticeContent(@PathVariable Long contentId) {
        PracticeContentDetailResponseDto response = practiceContentService.getPracticeContent(contentId);
        return ApiResponse.success("학습 콘텐츠를 조회했습니다.", response);
    }

    @GetMapping("/{contentId}/recommendations")
    public ApiResponse<PracticeContentRecommendationsResponseDto> getPracticeContentRecommendations(
            @PathVariable Long contentId
    ) {
        PracticeContentRecommendationsResponseDto response =
                practiceContentService.getPracticeContentRecommendations(contentId);
        return ApiResponse.success("콘텐츠 기반 추천을 조회했습니다.", response);
    }
}
