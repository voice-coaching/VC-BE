package org.example.voice.admin.application;

import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.entity.AdminAuditLog;
import org.example.voice.admin.domain.model.AdminCourseData.*;
import org.example.voice.admin.domain.model.AdminData.*;
import org.example.voice.admin.domain.port.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Edits a class and its ordered sentences atomically; old example/content rows remain readable. */
@Service @RequiredArgsConstructor @Transactional
public class AdminCourseService {
    private final AdminAccessService access;
    private final AdminCourseStore courses;
    private final AdminStore audit;
    private final AdminContentInvalidation invalidation;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<View> list(long actor, Query query) {
        access.require(actor); AdminValidation.page(query.page(),query.size());
        AdminValidation.text(query.keyword(),200,false);
        return courses.list(query);
    }
    @Transactional(readOnly = true)
    public View read(long actor, long id) { access.require(actor); return courses.read(id); }

    public View create(long actor, Draft draft, String reason) {
        access.require(actor); validate(draft, reason);
        if(draft.sentences().stream().anyMatch(s -> s.contentId()!=null || s.contentVersion()!=null)) AdminValidation.invalid();
        var now=OffsetDateTime.now(clock);
        var course=courses.create(draft,now);
        var saved=courses.replace(course,draft,now);
        changed(actor,saved,reason,"COURSE_CREATE",now);
        return saved;
    }
    public View edit(long actor, long id, int version, Draft draft, String reason) {
        access.require(actor); validate(draft,reason);
        var course=courses.lock(id);
        var current=courses.read(id);
        AdminValidation.version(current.version(),version);
        var seen=new HashSet<Long>();
        for(var sentence:draft.sentences()) {
            if(sentence.contentId()==null) { if(sentence.contentVersion()!=null)AdminValidation.invalid(); continue; }
            if(!seen.add(sentence.contentId())) AdminValidation.invalid();
            var previous=current.sentences().stream().filter(s->s.contentId().equals(sentence.contentId())).findFirst();
            if(previous.isEmpty() || !Objects.equals(previous.get().contentVersion(),sentence.contentVersion())) AdminValidation.conflict();
        }
        var now=OffsetDateTime.now(clock);
        var saved=courses.replace(course,draft,now);
        changed(actor,saved,reason,"COURSE_EDIT",now);
        return saved;
    }
    private void changed(long actor, View saved, String reason, String action, OffsetDateTime now) {
        audit.audit(AdminAuditLog.success(actor,action,"COURSE",saved.id(),reason,"title,description,courseType,difficulty,status,estimatedMinutes,sentences,revision",now));
        audit.flush(); invalidation.afterContentChange();
    }
    private void validate(Draft draft,String reason) {
        AdminValidation.text(reason,500,true);
        if(draft==null || draft.courseType()==null || draft.difficulty()==null || draft.status()==null
                || draft.estimatedMinutes()==null || draft.estimatedMinutes()<1 || draft.estimatedMinutes()>3600
                || draft.sentences()==null || draft.sentences().isEmpty() || draft.sentences().size()>10)AdminValidation.invalid();
        AdminValidation.text(draft.title(),255,true); AdminValidation.text(draft.description(),1000,false);
        for(var sentence:draft.sentences()) {
            if(sentence==null || sentence.focus()==null || sentence.estimatedSeconds()==null || sentence.estimatedSeconds()<1 || sentence.estimatedSeconds()>3600)AdminValidation.invalid();
            AdminValidation.text(sentence.title(),255,true); AdminValidation.text(sentence.text(),10000,true); AdminValidation.text(sentence.hint(),500,false);
        }
    }
}
