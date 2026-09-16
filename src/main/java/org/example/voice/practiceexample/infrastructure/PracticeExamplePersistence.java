package org.example.voice.practiceexample.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.voice.practiceexample.domain.PracticeExampleException;
import org.example.voice.practiceexample.domain.entity.*;
import org.example.voice.practiceexample.domain.model.ExampleData.*;
import org.example.voice.practiceexample.domain.port.PracticeExampleReader;
import org.example.voice.course.domain.entity.CourseStep;
import org.example.voice.practicecontent.domain.type.PublishStatus;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.List;

@Repository @RequiredArgsConstructor
public class PracticeExamplePersistence implements PracticeExampleReader {
    private final EntityManager em;
    private final Clock clock;
    public Examples examples(Long userId, Long courseId, Long stepId, Long sessionId) {
        var step = step(stepId);
        if (!step.getCourse().getId().equals(courseId)) throw PracticeExampleException.missing();
        PracticeExampleSet set;
        if (sessionId != null) {
            var ids = em.createQuery("select s.practiceExampleSetId from TrainingSession s where s.id=:id and s.userId=:user and s.courseStepId=:step", Long.class)
                    .setParameter("id", sessionId).setParameter("user", userId).setParameter("step", stepId).getResultList();
            if (ids.isEmpty()) throw PracticeExampleException.missing();
            set = ids.getFirst() == null ? null : em.find(PracticeExampleSet.class, ids.getFirst());
        } else {
            var pinned = em.createQuery("select s.practiceExampleSetId from TrainingSession s where s.userId=:user and s.courseStepId=:step and s.practiceExampleSetId is not null and s.status in (org.example.voice.training.domain.type.TrainingSessionStatus.RECORDING,org.example.voice.training.domain.type.TrainingSessionStatus.UPLOADING,org.example.voice.training.domain.type.TrainingSessionStatus.ANALYZING) order by s.id desc", Long.class)
                    .setParameter("user", userId).setParameter("step", stepId).setMaxResults(1).getResultList();
            set = pinned.isEmpty() ? em.createQuery("select r from PracticeExampleSet r where r.stepId=:step and r.publishedAt<=:now order by r.revision desc", PracticeExampleSet.class)
                    .setParameter("step", stepId).setParameter("now", OffsetDateTime.now(clock)).setMaxResults(1).getResultStream().findFirst().orElse(null)
                    : em.find(PracticeExampleSet.class, pinned.getFirst());
        }
        var entries = entries(set);
        return new Examples(courseId, stepId, set.getRevision(), entries.stream().map(e -> new Item(e.getId(), e.getOrder(), e.getContent().getScriptText(), e.getHint(), e.getFocus(), e.getLocale(), e.getContent().getId())).toList());
    }
    public Snapshot published(String id) {
        var example = em.find(PracticeExample.class, id);
        if (example == null) throw PracticeExampleException.missing();
        if (example.getContent().getStatus() != PublishStatus.PUBLISHED) throw PracticeExampleException.missing();
        step(example.getSet().getStepId());
        entries(example.getSet());
        return snapshot(example);
    }
    public Snapshot forSession(Long contentId, Long stepId) {
        var found = em.createQuery("select e from PracticeExample e join fetch e.set where e.content.id=:content", PracticeExample.class)
                .setParameter("content", contentId).getResultStream().findFirst();
        if (found.isEmpty()) return null;
        var example = found.get();
        if (stepId != null && !stepId.equals(example.getSet().getStepId())) throw new PracticeExampleException(409, "COURSE_CONTENT_MISMATCH");
        step(example.getSet().getStepId()); entries(example.getSet()); return snapshot(example);
    }
    private List<PracticeExample> entries(PracticeExampleSet set) {
        if (set == null) throw new PracticeExampleException(503, "PRACTICE_EXAMPLES_UNAVAILABLE");
        if (set.getPublishedAt().isAfter(OffsetDateTime.now(clock))) throw PracticeExampleException.missing();
        var entries = em.createQuery("select e from PracticeExample e join fetch e.content where e.set.id=:set order by e.order", PracticeExample.class)
                .setParameter("set", set.getId()).getResultList();
        if (entries.size() != 5) throw new PracticeExampleException(503, "PRACTICE_EXAMPLES_UNAVAILABLE");
        for (int i = 0; i < 5; i++) {
            var e = entries.get(i); var c = e.getContent();
            if (e.getOrder() != i + 1 || !"ko-KR".equals(e.getLocale()) || c.getOwnerId() != null || c.getStatus() != PublishStatus.PUBLISHED
                    || (c.getPublishedAt() != null && c.getPublishedAt().isAfter(OffsetDateTime.now(clock)))
                    || c.getScriptText() == null || c.getScriptText().isBlank() || c.getScriptText().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4500)
                throw new PracticeExampleException(503, "PRACTICE_EXAMPLES_UNAVAILABLE");
        }
        return entries;
    }
    private Snapshot snapshot(PracticeExample example) {
        var set = example.getSet(); return new Snapshot(example.getId(), set.getId(), set.getRevision(), set.getStepId(), set.getEducationRevisionId(), example.getContent().getScriptText());
    }
    private CourseStep step(Long id) {
        return em.createQuery("select s from CourseStep s join fetch s.course c where s.id=:id and c.status=:status", CourseStep.class)
                .setParameter("id", id).setParameter("status", PublishStatus.PUBLISHED).getResultStream().findFirst().orElseThrow(PracticeExampleException::missing);
    }
}
