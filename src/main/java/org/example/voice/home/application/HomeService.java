package org.example.voice.home.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.home.controller.dto.HomeDashboardResponseDto;
import org.example.voice.home.domain.port.HomeReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeService {

    private static final Long TEMP_LOGIN_USER_ID = 1L;

    private final HomeReader homeReader;

    @Transactional(readOnly = true)
    public HomeDashboardResponseDto getHomeDashboard() {
        Long userId = TEMP_LOGIN_USER_ID;
        return HomeDashboardResponseDto.from(
                homeReader.getTodayStatus(userId),
                homeReader.findRecommendations(userId, null, 1),
                homeReader.findRecentTraining(userId).orElse(null),
                homeReader.findCourseProgress(userId).orElse(null)
        );
    }
}
