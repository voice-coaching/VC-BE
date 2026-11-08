package org.example.voice.admin.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.model.AdminCourseData.*;
import org.example.voice.admin.domain.model.AdminData.*;
import org.example.voice.admin.domain.port.AdminCourseStore;
import org.example.voice.common.exception.*;
import org.example.voice.course.domain.entity.*;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.practiceexample.domain.entity.*;
import org.springframework.stereotype.Repository;

/** Uses real course/step/example relations; never infers class ownership from sentence titles. */
@Repository @RequiredArgsConstructor
public class AdminCoursePersistence implements AdminCourseStore {
    private final EntityManager em;
    public Page<View> list(Query query) {
        String keyword=query.keyword()==null?"":query.keyword().trim().toLowerCase(Locale.ROOT);
        var items=em.createQuery("select c from Course c where lower(c.title) like :keyword escape '!' order by c.id",Course.class)
                .setParameter("keyword","%"+escape(keyword)+"%").setFirstResult(query.page()*query.size()).setMaxResults(query.size()).getResultList();
        long total=em.createQuery("select count(c) from Course c where lower(c.title) like :keyword escape '!'",Long.class).setParameter("keyword","%"+escape(keyword)+"%").getSingleResult();
        return new Page<>(items.stream().map(c->view(c)).toList(),query.page(),query.size(),total);
    }
    private String escape(String keyword) { return keyword.replace("!","!!").replace("%","!%").replace("_","!_"); }
    public View read(long id) { return view(course(id)); }
    public Course lock(long id) {
        var course=course(id);em.refresh(course,LockModeType.PESSIMISTIC_WRITE);
        // Serialize against the legacy individual-content editor as well.
        read(id).sentences().stream().map(Sentence::contentId).sorted().forEach(contentId->em.find(PracticeContent.class,contentId,LockModeType.PESSIMISTIC_WRITE));
        return course;
    }
    public Course create(Draft draft,OffsetDateTime now) {
        var course=Course.authored(draft.courseType(),draft.title(),draft.description(),draft.difficulty(),draft.status(),draft.estimatedMinutes(),now);
        em.persist(course);em.flush();return course;
    }
    public View replace(Course course,Draft draft,OffsetDateTime now) {
        course.revise(draft.courseType(),draft.title(),draft.description(),draft.difficulty(),draft.status(),draft.estimatedMinutes(),now);
        var step=practiceStep(course.getId());
        if(step==null){step=CourseStep.practice(course,draft.title());em.persist(step);em.flush();}
        var previous=latest(step.getId());
        Long education=previous==null?null:previous.getEducationRevisionId();
        if(education==null) {
            var revisions=em.createQuery("select r from CourseStepRevision r where r.stepId=:step order by r.revision desc",CourseStepRevision.class).setParameter("step",step.getId()).setMaxResults(1).getResultList();
            if(revisions.isEmpty()) {var revision=CourseStepRevision.practice(step.getId(),draft.title());em.persist(revision);em.flush();education=revision.getId();}
            else education=revisions.getFirst().getId();
        }
        int revision=previous==null?1:previous.getRevision()+1;
        var set=PracticeExampleSet.published(step.getId(),revision,education,now);em.persist(set);
        int order=0;
        for(var sentence:draft.sentences()) {
            // Every new revision owns fresh content IDs, so session lookup cannot select an old set.
            var original=sentence.contentId()==null?null:em.find(PracticeContent.class,sentence.contentId());
            var content=PracticeContent.draft(ContentType.CLASS_PRACTICE,sentence.focus(),original==null?"OTHER":original.getCategory(),draft.difficulty(),sentence.title(),original==null?null:original.getDescription(),sentence.text(),original==null || original.getTargetPronunciations()==null?List.of():original.getTargetPronunciations(),sentence.estimatedSeconds(),now);
            if(draft.status()!=PublishStatus.DRAFT) content.changePublication(PublishStatus.PUBLISHED,now);
            if(draft.status()==PublishStatus.HIDDEN) content.changePublication(PublishStatus.HIDDEN,now);
            em.persist(content);
            em.persist(PracticeExample.authored(set,content,++order,sentence.hint(),sentence.focus()));
        }
        em.flush();return view(course);
    }
    private Course course(long id) {
        var course=em.find(Course.class,id);if(course==null)throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);return course;
    }
    private CourseStep practiceStep(long id) {
        var steps=em.createQuery("select s from CourseStep s where s.course.id=:id and s.retired=false and s.stepType=org.example.voice.course.domain.type.CourseStepType.PRACTICE",CourseStep.class).setParameter("id",id).setMaxResults(2).getResultList();
        if(steps.size()>1)throw new BaseException(ErrorCode.ADMIN_EDIT_CONFLICT);
        return steps.isEmpty()?null:steps.getFirst();
    }
    private PracticeExampleSet latest(long stepId) {
        return em.createQuery("select s from PracticeExampleSet s where s.stepId=:step order by s.revision desc,s.id desc",PracticeExampleSet.class).setParameter("step",stepId).setMaxResults(1).getResultStream().findFirst().orElse(null);
    }
    private View view(Course course) {
        var step=practiceStep(course.getId());var set=step==null?null:latest(step.getId());
        List<Sentence> sentences=set==null?List.of():em.createQuery("select e from PracticeExample e join fetch e.content c where e.set.id=:set and c.ownerId is null and c.deletedAt is null order by e.order,e.id",PracticeExample.class)
                .setParameter("set",set.getId()).getResultList().stream().map(e->{var c=e.getContent();return new Sentence(c.getId(),c.getVersion(),c.getTitle(),c.getScriptText(),e.getHint(),e.getFocus(),c.getEstimatedSeconds());}).toList();
        return new View(course.getId(),set==null?0:set.getRevision(),course.getCourseType(),course.getTitle(),course.getDescription(),course.getDifficulty(),course.getStatus(),course.getEstimatedMinutes(),course.getUpdatedAt(),sentences);
    }
}
