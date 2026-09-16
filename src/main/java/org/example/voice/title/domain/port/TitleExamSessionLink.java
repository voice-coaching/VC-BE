package org.example.voice.title.domain.port;

import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.domain.model.TrainingSessionCreatedData;

public interface TitleExamSessionLink {
    TrainingSessionCreatedData createSession(Long userId, Long examId, Long contentId, Long courseStepId, LearningFocus focus);
}
