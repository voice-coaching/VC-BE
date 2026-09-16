package org.example.voice.course.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.voice.course.domain.CourseEducationException;
import org.example.voice.course.domain.entity.*;
import org.example.voice.course.domain.model.*;
import org.example.voice.course.domain.port.CourseEducationReader;
import org.example.voice.practicecontent.domain.type.PublishStatus;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.util.List;

@Repository @RequiredArgsConstructor
public class CourseEducationPersistence implements CourseEducationReader {
    private final EntityManager em;
    private final ObjectMapper json;

    @Override public CourseEducationData detail(Long courseId, Long stepId, Long userId, Long sessionId) {
        CourseStep step = publishedStep(stepId);
        if (!step.getCourse().getId().equals(courseId)) missing();
        CourseStepRevision revision;
        if (sessionId != null) {
            var ids = em.createQuery("select s.courseEducationRevisionId from TrainingSession s where s.id=:session and s.userId=:user and s.courseStepId=:step", Long.class)
                    .setParameter("session", sessionId).setParameter("user", userId).setParameter("step", stepId).getResultList();
            if (ids.isEmpty()) throw new CourseEducationException(404, "RESOURCE_NOT_FOUND");
            revision = ids.getFirst() == null ? null : em.find(CourseStepRevision.class, ids.getFirst());
        } else {
            var active = em.createQuery("select s.courseEducationRevisionId from TrainingSession s where s.userId=:user and s.courseStepId=:step and s.courseEducationRevisionId is not null and s.status in (org.example.voice.training.domain.type.TrainingSessionStatus.RECORDING, org.example.voice.training.domain.type.TrainingSessionStatus.UPLOADING, org.example.voice.training.domain.type.TrainingSessionStatus.ANALYZING) order by s.id desc", Long.class)
                    .setParameter("user", userId).setParameter("step", stepId).setMaxResults(1).getResultList();
            revision = active.isEmpty() ? latest(stepId) : em.find(CourseStepRevision.class, active.getFirst());
        }
        if (revision == null || !revision.getStepId().equals(stepId)) unavailable();
        List<CourseBlock> blocks = blocks(revision);
        var progress = em.createQuery("select p from UserCourseProgress p where p.userId=:user and p.course.id=:course", UserCourseProgress.class)
                .setParameter("user", userId).setParameter("course", courseId).getResultStream().findFirst();
        boolean completed = progress.map(p -> {
            if (p.getStatus() == org.example.voice.course.domain.type.CourseProgressStatus.COMPLETED) return true;
            if (p.getProgressPercent().signum() == 0 || p.getLastStepId() == null) return false;
            CourseStep last = em.find(CourseStep.class, p.getLastStepId());
            return last != null && last.getCourse().getId().equals(courseId) && last.getStepOrder() >= step.getStepOrder();
        }).orElse(false);
        return new CourseEducationData(stepId, courseId, revision.getStepOrder(), revision.getStepType(),
                revision.getTitle(), revision.getSubtitle(), revision.getRevision(), blocks, completed);
    }

    @Override public Long revisionForSession(Long stepId, Long contentId) {
        if (stepId == null) return null;
        CourseStep step = publishedStep(stepId);
        CourseStepRevision revision = latest(stepId);
        List<CourseBlock> blocks = blocks(revision);
        boolean matches = step.getPracticeContent() != null && step.getPracticeContent().getId().equals(contentId);
        matches |= blocks.stream().anyMatch(b -> "PRACTICE_PROMPT".equals(b.type()) && contentId.equals(b.practiceContentId()));
        if (!matches) throw new CourseEducationException(409, "COURSE_CONTENT_MISMATCH");
        return revision.getId();
    }
    private CourseStep publishedStep(Long id) {
        return em.createQuery("select s from CourseStep s join fetch s.course c where s.id=:id and c.status=:status", CourseStep.class)
                .setParameter("id", id).setParameter("status", PublishStatus.PUBLISHED).getResultStream().findFirst()
                .orElseThrow(() -> new CourseEducationException(404, "RESOURCE_NOT_FOUND"));
    }
    private CourseStepRevision latest(Long stepId) {
        return em.createQuery("select r from CourseStepRevision r where r.stepId=:step order by r.revision desc", CourseStepRevision.class)
                .setParameter("step", stepId).setMaxResults(1).getResultStream().findFirst()
                .orElseThrow(() -> new CourseEducationException(503, "COURSE_CONTENT_UNAVAILABLE"));
    }
    private List<CourseBlock> blocks(CourseStepRevision revision) {
        try {
            var result = List.of(json.readValue(revision.getBlocksJson(), CourseBlock[].class));
            if (result.isEmpty() || result.size() > 100) unavailable();
            return result;
        } catch (RuntimeException e) { throw new CourseEducationException(503, "COURSE_CONTENT_UNAVAILABLE"); }
    }
    private void missing() { throw new CourseEducationException(404, "RESOURCE_NOT_FOUND"); }
    private void unavailable() { throw new CourseEducationException(503, "COURSE_CONTENT_UNAVAILABLE"); }
}
