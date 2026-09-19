package org.example.voice.home.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.home.controller.dto.HomeDashboardResponseDto;
import org.example.voice.home.domain.port.HomeReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final HomeReader homeReader;

    @Transactional(readOnly = true)
    public HomeDashboardResponseDto getHomeDashboard(Long userId) {
        return HomeDashboardResponseDto.from(
                homeReader.getTodayStatus(userId),
                homeReader.findRecommendations(userId, null, 1).items(),
                homeReader.findRecentTraining(userId).orElse(null),
                homeReader.findCourseProgress(userId).orElse(null)
        );
    }
}
