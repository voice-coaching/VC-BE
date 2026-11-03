package org.example.voice.course.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.course.domain.CourseEducationException;
import org.example.voice.course.domain.model.CourseEducationData;
import org.example.voice.course.domain.port.CourseEducationReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class CourseEducationService {
    private final CourseEducationReader education;
    public CourseEducationData detail(Long courseId, Long stepId, Long userId, Long sessionId) {

        return education.detail(courseId, stepId, userId, sessionId);
    }
}
