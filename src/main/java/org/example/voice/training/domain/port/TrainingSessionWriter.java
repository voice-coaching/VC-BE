package org.example.voice.training.domain.port;

import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.domain.model.TrainingSessionCancellationData;
import org.example.voice.training.domain.model.TrainingSessionCompletionData;
import org.example.voice.training.domain.model.TrainingSessionCreatedData;

public interface TrainingSessionWriter {

    TrainingSessionCreatedData create(Long userId, Long contentId, Long courseStepId, LearningFocus learningFocus);

    void beginUpload(Long sessionId);

    void startAnalysis(Long sessionId);

    void assertAnalysisRetryAllowed(Long sessionId);

    TrainingSessionCompletionData complete(Long sessionId, Integer totalLearningSeconds);

    TrainingSessionCancellationData cancel(Long sessionId);
}
