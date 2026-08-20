package org.example.voice.practicecontent.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.practicecontent.controller.dto.PracticeContentDetailResponseDto;
import org.example.voice.practicecontent.controller.dto.PracticeContentListResponseDto;
import org.example.voice.practicecontent.controller.dto.PracticeContentNextConditionDto;
import org.example.voice.practicecontent.controller.dto.PracticeContentQueryConditionDto;
import org.example.voice.practicecontent.controller.dto.PracticeContentRecommendationResponseDto;
import org.example.voice.practicecontent.controller.dto.PracticeContentRecommendationsResponseDto;
import org.example.voice.practicecontent.domain.port.PracticeContentReader;
import org.example.voice.practicecontent.exception.ContentNotFoundException;
import org.example.voice.practicecontent.exception.NextContentNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PracticeContentService {

    private final PracticeContentReader practiceContentReader;

    @Transactional(readOnly = true)
    public PracticeContentListResponseDto getPracticeContents(PracticeContentQueryConditionDto condition) {
        return PracticeContentListResponseDto.from(practiceContentReader.findPracticeContents(condition.normalized()));
    }

    @Transactional(readOnly = true)
    public PracticeContentDetailResponseDto getPracticeContent(Long contentId) {
        return practiceContentReader.findPracticeContent(contentId)
                .map(PracticeContentDetailResponseDto::from)
                .orElseThrow(ContentNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public PracticeContentRecommendationResponseDto getNextPracticeContent(PracticeContentNextConditionDto condition) {
        if (condition == null || condition.type() == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return practiceContentReader.findNextPracticeContent(condition)
                .map(PracticeContentRecommendationResponseDto::from)
                .orElseThrow(NextContentNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public PracticeContentRecommendationsResponseDto getPracticeContentRecommendations(Long contentId) {
        return practiceContentReader.findRecommendationsByContentId(contentId)
                .map(PracticeContentRecommendationsResponseDto::from)
                .orElseThrow(ContentNotFoundException::new);
    }
}
