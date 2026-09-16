package org.example.voice.practiceexample.domain.port;

import org.example.voice.practiceexample.domain.model.ExampleData.*;

public interface PracticeExampleReader {
    Examples examples(Long userId, Long courseId, Long stepId, Long sessionId);
    Snapshot published(String exampleId);
    Snapshot forSession(Long contentId, Long stepId);
}
