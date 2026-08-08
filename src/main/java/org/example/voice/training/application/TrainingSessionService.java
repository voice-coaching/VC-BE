package org.example.voice.training.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.training.domain.TrainingSessionReader;
import org.example.voice.training.domain.TrainingSessionWriter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrainingSessionService {

    private final TrainingSessionReader trainingSessionReader;
    private final TrainingSessionWriter trainingSessionWriter;
}
