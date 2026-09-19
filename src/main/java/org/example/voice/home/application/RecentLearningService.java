package org.example.voice.home.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.home.controller.dto.RecentTrainingSessionResponseDto;
import org.example.voice.home.domain.port.HomeReader;
import org.example.voice.home.exception.RecentTrainingNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecentLearningService {

    private final HomeReader homeReader;

    @Transactional(readOnly = true)
    public RecentTrainingSessionResponseDto getRecentTrainingSession(Long userId) {
        return homeReader.findRecentTraining(userId)
                .map(RecentTrainingSessionResponseDto::from)
                .orElseThrow(RecentTrainingNotFoundException::new);
    }
}
