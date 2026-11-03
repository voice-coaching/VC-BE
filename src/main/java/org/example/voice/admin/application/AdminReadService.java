package org.example.voice.admin.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.model.AdminData.*;
import org.example.voice.admin.domain.port.AdminStore;
import org.example.voice.common.exception.*;
import org.example.voice.payment.domain.Membership;
import org.example.voice.payment.domain.MembershipReader;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AdminReadService {
    private final AdminAccessService access;
    private final AdminStore store;
    private final MembershipReader memberships;
    public record Member(UserView user, Membership membership, List<String> loginProviders) {}
    public Page<Member> users(long actor,UserFilter f) {
        access.require(actor); query(f.query());
        var p=store.users(f);
        var levels=memberships.readAll(p.items().stream().map(UserView::id).toList());
        return new Page<>(p.items().stream().map(u -> new Member(u,levels.getOrDefault(u.id(),Membership.free()),List.of())).toList(),p.page(),p.size(),p.totalElements());
    }
    public Member user(long actor,long id) {
        access.require(actor); var u=store.user(id).orElseThrow(AdminReadService::missing);
        return new Member(u,memberships.read(id),store.loginProviders(id));
    }
    public Page<ContentView> contents(long actor,ContentFilter f) { access.require(actor);query(f.query());AdminValidation.text(f.category(),100,false);return store.contents(f); }
    public ContentView content(long actor,long id) { access.require(actor);return store.content(id).orElseThrow(AdminReadService::missing); }
    public List<Category> categories(long actor,ContentType type) { access.require(actor);if(type==null)AdminValidation.invalid();return store.categories(type); }
    public Page<NoticeView> notices(long actor,NoticeFilter f) { access.require(actor);query(f.query());return store.notices(f); }
    public NoticeView notice(long actor,long id) { access.require(actor);return store.notice(id).orElseThrow(AdminReadService::missing); }
    public Page<InquiryView> inquiries(long actor,InquiryFilter f) { access.require(actor);query(f.query());AdminValidation.text(f.category(),50,false);return store.inquiries(f); }
    public InquiryView inquiry(long actor,long id) { access.require(actor);return store.inquiry(id).orElseThrow(AdminReadService::missing); }
    public Page<AuditView> audits(long actor,AuditFilter f) {
        access.require(actor);AdminValidation.page(f.page(),f.size());
        AdminValidation.text(f.action(),80,false);AdminValidation.text(f.targetType(),30,false);AdminValidation.text(f.targetId(),100,false);
        if((f.actorId()!=null && f.actorId()<1) || (f.from()!=null && f.to()!=null && f.from().isAfter(f.to())))AdminValidation.invalid();
        return store.auditLogs(f);
    }
    private static void query(Query q) { AdminValidation.page(q.page(),q.size());AdminValidation.text(q.keyword(),200,false); }
    private static BaseException missing() { return new BaseException(ErrorCode.RESOURCE_NOT_FOUND); }
}
