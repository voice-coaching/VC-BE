package org.example.voice.practiceexample.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.practiceexample.domain.PracticeExampleException;
import org.example.voice.practiceexample.domain.model.ExampleData.*;
import org.example.voice.practiceexample.domain.port.PracticeExampleReader;
import org.example.voice.user.domain.port.UserReader;
import org.example.voice.user.domain.type.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class PracticeExampleService {
    private final PracticeExampleReader examples;
    private final UserReader users;
    public Examples listForCourse(Long userId, Long courseId, Long sessionId) {
        active(userId);

        Long stepId = examples.practiceStepForCourse(courseId);
        return examples.examples(userId, courseId, stepId, sessionId);
    }
    public Examples list(Long userId, Long courseId, Long stepId, Long sessionId) {
        active(userId);

        return examples.examples(userId, courseId, stepId, sessionId);
    }
    public Snapshot audioSource(Long userId, String id) {
        active(userId);

        return examples.published(id);
    }
    private void active(Long userId) {
        var user = users.findById(userId).orElseThrow(PracticeExampleException::missing);

    }
}
