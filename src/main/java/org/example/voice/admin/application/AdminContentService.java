package org.example.voice.admin.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.entity.AdminAuditLog;
import org.example.voice.admin.domain.model.AdminData.*;
import org.example.voice.admin.domain.port.*;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service @RequiredArgsConstructor @Transactional
public class AdminContentService {
    private final AdminAccessService access;
    private final AdminStore store;
    private final AdminContentInvalidation invalidation;
    private final Clock clock;

    public ContentView create(long actor,ContentDraft draft,String reason) {
        access.require(actor); validate(draft,reason);
        var content=PracticeContent.draft(draft.type(),draft.focus(),draft.category(),draft.difficulty(),draft.title(),draft.description(),
                draft.scriptText(),draft.targets(),draft.seconds(),OffsetDateTime.now(clock));
        store.saveContent(content);
        audit(actor,"CONTENT_CREATE",content,reason,"type,focus,category,difficulty,title,description,scriptText,targetPronunciations,estimatedSeconds,status");
        store.flush(); invalidation.afterContentChange(); return store.view(content);
    }
    public ContentView edit(long actor,long id,long version,ContentDraft draft,String reason) {
        access.require(actor);validate(draft,reason);
        var content=store.lockContent(id);AdminValidation.version(content.getVersion(),version);
        if(content.getContentType()!=draft.type())AdminValidation.conflict();
        try { content.editDraft(draft.focus(),draft.category(),draft.difficulty(),draft.title(),draft.description(),draft.scriptText(),draft.targets(),draft.seconds(),OffsetDateTime.now(clock)); }
        catch(IllegalStateException error) { AdminValidation.conflict(); }
        audit(actor,"CONTENT_EDIT",content,reason,"focus,category,difficulty,title,description,scriptText,targetPronunciations,estimatedSeconds");
        store.flush();invalidation.afterContentChange();return store.view(content);
    }
    public ContentView publication(long actor,long id,long version,PublishStatus status,String reason) {
        access.require(actor);AdminValidation.text(reason,500,true);
        var content=store.lockContent(id);AdminValidation.version(content.getVersion(),version);
        // Revalidate taxonomy and script even for existing drafts, including SQL-created ones.
        if(status==PublishStatus.PUBLISHED) validate(new ContentDraft(content.getContentType(),content.getLearningFocus(),content.getCategory(),
                content.getDifficulty(),content.getTitle(),content.getDescription(),content.getScriptText(),
                content.getTargetPronunciations(),content.getEstimatedSeconds()),reason);
        try { content.changePublication(status,OffsetDateTime.now(clock)); }
        catch(IllegalStateException error) { AdminValidation.conflict(); }
        audit(actor,"CONTENT_PUBLICATION",content,reason,"status,publishedAt");
        store.flush();invalidation.afterContentChange();return store.view(content);
    }
    private void validate(ContentDraft d,String reason) {
        AdminValidation.text(reason,500,true);
        if(d==null || d.type()==null || d.focus()==null || d.difficulty()==null)AdminValidation.invalid();
        AdminValidation.text(d.category(),100,true);AdminValidation.text(d.title(),255,true);
        AdminValidation.text(d.description(),1000,false);AdminValidation.text(d.scriptText(),10000,true);
        if("CUSTOM".equals(d.category()) || !store.categoryExists(d.type(),d.category()))AdminValidation.invalid();
        if(d.targets()==null || d.targets().size()>100 || d.seconds()==null || d.seconds()<1 || d.seconds()>3600)AdminValidation.invalid();
        d.targets().forEach(t -> AdminValidation.text(t,200,true));
    }
    private void audit(long actor,String action,PracticeContent c,String reason,String fields) {
        store.audit(AdminAuditLog.success(actor,action,"CONTENT",c.getId(),reason,fields,OffsetDateTime.now(clock)));
    }
}
