package org.example.voice.training.domain.port;

import org.example.voice.training.domain.model.AnalysisProgressData;

import java.util.Optional;

public interface TrainingAnalysisReader {

    boolean existsRunningAnalysis(Long recordingId);

    boolean existsAnalysis(Long recordingId);

    Optional<AnalysisProgressData> findLatestBySelectedRecording(Long sessionId, Long userId);

    Optional<AnalysisProgressData> findLatestFailedBySelectedRecording(Long sessionId, Long userId);

    boolean existsCompletedAnalysisForSelectedRecording(Long sessionId, Long userId);

}
