package org.example.voice.home.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.home.controller.dto.RecommendationResponseDto;
import org.example.voice.home.controller.dto.RecommendationSearchConditionDto;
import org.example.voice.home.domain.port.HomeReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;

    private final HomeReader homeReader;

    @Transactional(readOnly = true)
    public RecommendationResponseDto getRecommendations(Long userId, RecommendationSearchConditionDto condition) {
        int limit = resolveLimit(condition);
        return RecommendationResponseDto.from(
                homeReader.findRecommendations(userId, condition == null ? null : condition.type(), limit)
        );
    }

    private int resolveLimit(RecommendationSearchConditionDto condition) {
        if (condition == null || condition.limit() == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(condition.limit(), 1), MAX_LIMIT);
    }
}
