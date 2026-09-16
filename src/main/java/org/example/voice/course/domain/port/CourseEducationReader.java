package org.example.voice.course.domain.port;

import org.example.voice.course.domain.model.CourseEducationData;

public interface CourseEducationReader {
    CourseEducationData detail(Long courseId, Long stepId, Long userId, Long sessionId);
    Long revisionForSession(Long stepId, Long contentId);
}
