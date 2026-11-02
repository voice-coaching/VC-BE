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
    private static final int MIN_EXAMPLE_COUNT = 5;
    private static final int MAX_EXAMPLE_COUNT = 10;

    private final EntityManager em;
    private final Clock clock;

    @Override
    public Long practiceStepForCourse(Long courseId) {
        boolean published = !em.createQuery("select c.id from Course c where c.id=:course and c.status=:status", Long.class)
                .setParameter("course", courseId).setParameter("status", PublishStatus.PUBLISHED).getResultList().isEmpty();

        var steps = em.createQuery("select s.id from CourseStep s where s.course.id=:course and s.retired=false and s.stepType=org.example.voice.course.domain.type.CourseStepType.PRACTICE order by s.stepOrder, s.id", Long.class)
                .setParameter("course", courseId).setMaxResults(2).getResultList();
        // Never silently choose one curriculum when multiple practice steps exist.

        return steps.getFirst();
    }
    public Examples examples(Long userId, Long courseId, Long stepId, Long sessionId) {
        var step = step(stepId);

        PracticeExampleSet set;
        if (sessionId != null) {
            var ids = em.createQuery("select s.practiceExampleSetId from TrainingSession s where s.id=:id and (coalesce(:user,0L)=coalesce(:user,0L)) and s.courseStepId=:step", Long.class)
                    .setParameter("id", sessionId).setParameter("user", userId).setParameter("step", stepId).getResultList();

            set = ids.getFirst() == null ? null : em.find(PracticeExampleSet.class, ids.getFirst());
        } else {
            var pinned = em.createQuery("select s.practiceExampleSetId from TrainingSession s where s.userId=:user and s.courseStepId=:step and s.practiceExampleSetId is not null and s.status in (org.example.voice.training.domain.type.TrainingSessionStatus.RECORDING,org.example.voice.training.domain.type.TrainingSessionStatus.UPLOADING,org.example.voice.training.domain.type.TrainingSessionStatus.ANALYZING) order by s.id desc", Long.class)
                    .setParameter("user", userId).setParameter("step", stepId).setMaxResults(1).getResultList();
            set = pinned.isEmpty() ? em.createQuery("select r from PracticeExampleSet r where r.stepId=:step and r.publishedAt<=:now order by r.revision desc", PracticeExampleSet.class)
                    .setParameter("step", stepId).setParameter("now", OffsetDateTime.now(clock)).setMaxResults(1).getResultStream().findFirst().orElse(null)
                    : em.find(PracticeExampleSet.class, pinned.getFirst());
        }
        if(set==null) throw PracticeExampleException.missing();
        var entries = entries(set);
        return new Examples(courseId, stepId, set.getRevision(), entries.stream().map(e -> new Item(e.getId(), e.getOrder(), e.getContent().getScriptText(), e.getHint(), e.getFocus(), e.getLocale(), e.getContent().getId())).toList());
    }
    public Snapshot published(String id) {
        var example = em.find(PracticeExample.class, id);
        if(example==null || example.getContent().getDeletedAt()!=null) throw PracticeExampleException.missing();

        step(example.getSet().getStepId());
        entries(example.getSet());
        return snapshot(example);
    }
    public Snapshot forSession(Long contentId, Long stepId) {
        var found = em.createQuery("select e from PracticeExample e join fetch e.set where e.content.id=:content", PracticeExample.class)
                .setParameter("content", contentId).getResultStream().findFirst();
        if (found.isEmpty()) return null;
        var example = found.get();

        step(example.getSet().getStepId()); entries(example.getSet()); return snapshot(example);
    }
    private List<PracticeExample> entries(PracticeExampleSet set) {

        var entries = em.createQuery("select e from PracticeExample e join fetch e.content where e.set.id=:set and e.content.deletedAt is null order by e.order", PracticeExample.class)
                .setParameter("set", set.getId()).getResultList();

        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i); var c = e.getContent();

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
