package org.example.voice.admin.domain.model;

import java.time.OffsetDateTime;
import java.util.List;
import org.example.voice.course.domain.type.CourseType;
import org.example.voice.practicecontent.domain.type.*;

/** Admin editing models; a saved sentence list is one new, complete course revision. */
public final class AdminCourseData {
    private AdminCourseData() {}
    public record Sentence(Long contentId, Long contentVersion, String title, String text,
                           String hint, LearningFocus focus, Integer estimatedSeconds) {}
    public record Draft(CourseType courseType, String title, String description, Difficulty difficulty,
                        PublishStatus status, Integer estimatedMinutes, List<Sentence> sentences) {}
    public record View(long id, int version, CourseType courseType, String title, String description,
                       Difficulty difficulty, PublishStatus status, Integer estimatedMinutes,
                       OffsetDateTime updatedAt, List<Sentence> sentences) {}
}
