package org.example.voice.admin.domain.port;

import java.util.List;
import java.util.Optional;
import org.example.voice.admin.domain.model.AdminData.*;
import org.example.voice.admin.domain.entity.AdminAuditLog;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.support.domain.entity.*;

public interface AdminStore {
    Optional<Access> activeAdmin(long userId);
    Page<UserView> users(UserFilter filter);
    Optional<UserView> user(long id);
    List<String> loginProviders(long userId);
    Page<ContentView> contents(ContentFilter filter);
    Optional<ContentView> content(long id);
    List<Category> categories(ContentType type);
    boolean categoryExists(ContentType type, String code);
    PracticeContent lockContent(long id);
    void saveContent(PracticeContent content);
    void retireReferenceAudios(long contentId, java.time.OffsetDateTime now);
    ContentView view(PracticeContent content);
    Page<NoticeView> notices(NoticeFilter filter);
    Optional<NoticeView> notice(long id);
    Notice lockNotice(long id);
    void saveNotice(Notice notice);
    void deleteNotice(Notice notice);
    NoticeView view(Notice notice);
    Page<InquiryView> inquiries(InquiryFilter filter);
    Optional<InquiryView> inquiry(long id);
    Inquiry lockInquiry(long id);
    InquiryView view(Inquiry inquiry);
    Page<AuditView> auditLogs(AuditFilter filter);
    void audit(AdminAuditLog log);
    void flush();
}
