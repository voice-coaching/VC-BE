package org.example.voice.admin.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.entity.AdminAuditLog;
import org.example.voice.admin.domain.model.AdminData.*;
import org.example.voice.admin.domain.port.AdminStore;
import org.example.voice.support.domain.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service @RequiredArgsConstructor @Transactional
public class AdminSupportService {
    private final AdminAccessService access;
    private final AdminStore store;
    private final Clock clock;
    public NoticeView createNotice(long actor,NoticeDraft d,String reason) {
        access.require(actor);validate(d,reason);
        var n=Notice.scheduled(d.title(),d.summary(),d.sections(),d.pinned(),d.publishedAt());
        n.edit(d.title(),d.summary(),d.sections(),d.pinned(),d.published(),d.publishedAt());
        store.saveNotice(n);audit(actor,"NOTICE_CREATE","NOTICE",n.getId(),reason,"title,summary,sections,pinned,published,publishedAt");
        store.flush();return store.view(n);
    }
    public NoticeView editNotice(long actor,long id,long version,NoticeDraft d,String reason) {
        access.require(actor);validate(d,reason);
        var n=store.lockNotice(id);AdminValidation.version(n.getVersion(),version);
        n.edit(d.title(),d.summary(),d.sections(),d.pinned(),d.published(),d.publishedAt());
        audit(actor,"NOTICE_EDIT","NOTICE",id,reason,"title,summary,sections,pinned,published,publishedAt");
        store.flush();return store.view(n);
    }
    public void deleteNotice(long actor,long id,long version,String reason) {
        access.require(actor);AdminValidation.text(reason,500,true);
        var notice=store.lockNotice(id);AdminValidation.version(notice.getVersion(),version);
        store.deleteNotice(notice);
        audit(actor,"NOTICE_DELETE","NOTICE",id,reason,"deleted");store.flush();
    }
    public InquiryView answer(long actor,long id,long version,String answer,String reason) {
        access.require(actor);AdminValidation.text(reason,500,true);AdminValidation.text(answer,4000,true);
        var i=store.lockInquiry(id);AdminValidation.version(i.getVersion(),version);
        try { i.answer(answer,OffsetDateTime.now(clock)); } catch(IllegalStateException error) { AdminValidation.conflict(); }
        audit(actor,"INQUIRY_ANSWER","INQUIRY",id,reason,"answer,status,answeredAt");
        store.flush();return store.view(i);
    }
    public InquiryView status(long actor,long id,long version,Inquiry.Status target,String reason) {
        access.require(actor);AdminValidation.text(reason,500,true);
        var i=store.lockInquiry(id);AdminValidation.version(i.getVersion(),version);
        try { i.transition(target); } catch(IllegalStateException error) { AdminValidation.conflict(); }
        audit(actor,"INQUIRY_STATUS","INQUIRY",id,reason,"status");store.flush();return store.view(i);
    }
    private void validate(NoticeDraft d,String reason) {
        AdminValidation.text(reason,500,true);
        if(d==null)AdminValidation.invalid();
        AdminValidation.text(d.title(),200,true);AdminValidation.text(d.summary(),500,true);
        if(d.publishedAt()==null || d.sections()==null || d.sections().isEmpty() || d.sections().size()>30)AdminValidation.invalid();
        int characters=0;
        for(var section:d.sections()) {
            if(section==null)AdminValidation.invalid();
            AdminValidation.text(section.title(),200,true);
            if(section.paragraphs()==null || section.paragraphs().isEmpty() || section.paragraphs().size()>50)AdminValidation.invalid();
            for(var paragraph:section.paragraphs()) {
                AdminValidation.text(paragraph,4000,true);
                if(paragraph.matches("(?s).*<[^>]+>.*"))AdminValidation.invalid();
                characters+=paragraph.length();
            }
        }
        if(characters>30000)AdminValidation.invalid();
    }
    private void audit(long actor,String action,String type,long id,String reason,String fields) {
        store.audit(AdminAuditLog.success(actor,action,type,id,reason,fields,OffsetDateTime.now(clock)));
    }
}
