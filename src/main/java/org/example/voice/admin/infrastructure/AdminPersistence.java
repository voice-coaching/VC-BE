package org.example.voice.admin.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.entity.AdminAuditLog;
import org.example.voice.admin.domain.model.AdminData.*;
import org.example.voice.admin.domain.port.AdminStore;
import org.example.voice.common.exception.*;
import org.example.voice.practicecontent.domain.entity.*;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.support.domain.entity.*;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.type.*;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.util.function.Function;

@Repository @RequiredArgsConstructor
public class AdminPersistence implements AdminStore {
    private final EntityManager em;

    @Override public Optional<Access> activeAdmin(long id) {
        // A scalar query bypasses managed User snapshots and the login cache.
        return em.createQuery("select u.id,u.nickname from User u where u.id=:id and u.role=:role "
                + "and u.status=:status and u.deletedAt is null", Object[].class)
                .setParameter("id",id).setParameter("role",UserRole.ADMIN).setParameter("status",UserStatus.ACTIVE)
                .getResultStream().findFirst().map(r -> new Access((Long)r[0],(String)r[1],"ADMIN"));
    }

    @Override public Page<UserView> users(UserFilter f) {
        var p = new LinkedHashMap<String,Object>();
        String where=" where u.status<>org.example.voice.user.domain.type.UserStatus.WITHDRAWN and u.deletedAt is null" + keyword(f.query(),p,"u.email","u.nickname","cast(u.id as string)")
                + eq(p,"status",f.status(),"u.status") + eq(p,"role",f.role(),"u.role");
        return page("User","u",where,"u.createdAt desc,u.id desc",p,f.query(),User.class,this::userView);
    }
    @Override public Optional<UserView> user(long id) { return Optional.ofNullable(em.find(User.class,id)).filter(u->u.getStatus()!=UserStatus.WITHDRAWN && u.getDeletedAt()==null).map(this::userView); }
    private UserView userView(User u) {
        String email=u.getEmail();
        if(email!=null) { int at=email.indexOf('@'); email=at>0?email.substring(0,1)+"***"+email.substring(at):"***"; }
        return new UserView(u.getId(),email,u.getNickname(),u.getStatus().name(),u.getRole().name(),u.getCreatedAt());
    }
    @Override public List<String> loginProviders(long user) {
        var providers=new ArrayList<>(em.createQuery("select s.provider from SocialAccount s where s.userId=:id order by s.provider",org.example.voice.auth.domain.type.OAuthProvider.class)
                .setParameter("id",user).getResultList().stream().map(Enum::name).toList());
        if(Boolean.TRUE.equals(em.createQuery("select (u.password is not null) from User u where u.id=:id",Boolean.class).setParameter("id",user).getSingleResult()))
            providers.addFirst("LOCAL");
        return List.copyOf(providers);
    }

    @Override public Page<ContentView> contents(ContentFilter f) {
        var p=new LinkedHashMap<String,Object>();
        String where=" where c.ownerId is null and c.deletedAt is null" + keyword(f.query(),p,"c.title")
                + eq(p,"type",f.type(),"c.contentType") + eq(p,"category",f.category(),"c.category")
                + eq(p,"status",f.status(),"c.status") + eq(p,"difficulty",f.difficulty(),"c.difficulty");
        return page("PracticeContent","c",where,"c.createdAt desc,c.id desc",p,f.query(),PracticeContent.class,this::view);
    }
    @Override public Optional<ContentView> content(long id) {
        return em.createQuery("select c from PracticeContent c where c.id=:id and c.ownerId is null and c.deletedAt is null",PracticeContent.class)
                .setParameter("id",id).getResultStream().findFirst().map(this::view);
    }
    @Override public PracticeContent lockContent(long id) {
        return em.createQuery("select c from PracticeContent c where c.id=:id and c.ownerId is null and c.deletedAt is null",PracticeContent.class)
                .setParameter("id",id).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst().orElseThrow(AdminPersistence::missing);
    }
    @Override public void retireReferenceAudios(long contentId, java.time.OffsetDateTime now) {
        em.createQuery("update ReferenceAudio r set r.retiredAt=:now, r.primary=false where r.content.id=:id and r.retiredAt is null")
                .setParameter("now",now).setParameter("id",contentId).executeUpdate();
    }
    @Override public void saveContent(PracticeContent c) { em.persist(c); }
    @Override public ContentView view(PracticeContent c) {
        return new ContentView(c.getId(),c.getVersion(),c.getContentType(),c.getLearningFocus(),c.getCategory(),c.getDifficulty(),
                c.getTitle(),c.getDescription(),c.getScriptText(),c.getTargetPronunciations(),c.getEstimatedSeconds(),c.getStatus(),c.getPublishedAt(),c.getUpdatedAt());
    }
    @Override public List<Category> categories(ContentType type) {
        return em.createQuery("select t from ContentCategory t where t.contentType=:type order by t.sortOrder,t.code",ContentCategory.class)
                .setParameter("type",type).getResultList().stream().map(t -> new Category(t.getContentType(),t.getCode(),t.getLabel(),t.getSortOrder())).toList();
    }
    @Override public boolean categoryExists(ContentType type,String code) {
        return em.createQuery("select count(t) from ContentCategory t where t.contentType=:type and t.code=:code",Long.class)
                .setParameter("type",type).setParameter("code",code).getSingleResult()>0;
    }

    @Override public Page<NoticeView> notices(NoticeFilter f) {
        var p=new LinkedHashMap<String,Object>();
        return page("Notice","n"," where 1=1"+keyword(f.query(),p,"n.title")+eq(p,"published",f.published(),"n.published"),
                "n.publishedAt desc,n.id desc",p,f.query(),Notice.class,this::view);
    }
    @Override public Optional<NoticeView> notice(long id) { return Optional.ofNullable(em.find(Notice.class,id)).map(this::view); }
    @Override public Notice lockNotice(long id) { var n=em.find(Notice.class,id,LockModeType.PESSIMISTIC_WRITE);if(n==null)throw missing();return n; }
    @Override public void saveNotice(Notice n) { em.persist(n); }
    @Override public NoticeView view(Notice n) { return new NoticeView(n.getId(),n.getVersion(),n.getTitle(),n.getSummary(),n.getSections(),n.isPinned(),n.isPublished(),n.getPublishedAt()); }
    @Override public Page<InquiryView> inquiries(InquiryFilter f) {
        var p=new LinkedHashMap<String,Object>();
        return page("Inquiry","i"," where 1=1"+keyword(f.query(),p,"i.subject")
                        +eq(p,"status",f.status(),"i.status")+eq(p,"category",f.category(),"i.category"),
                "i.createdAt desc,i.id desc",p,f.query(),Inquiry.class,this::view);
    }
    @Override public Optional<InquiryView> inquiry(long id) { return Optional.ofNullable(em.find(Inquiry.class,id)).map(this::view); }
    @Override public Inquiry lockInquiry(long id) { var i=em.find(Inquiry.class,id,LockModeType.PESSIMISTIC_WRITE);if(i==null)throw missing();return i; }
    @Override public InquiryView view(Inquiry i) {
        return new InquiryView(i.getId(),i.getVersion(),i.getUserId(),i.getCategory(),i.getSubject(),i.getBody(),i.getRelatedSessionId(),
                i.getReplyEmail(),i.getStatus().name(),i.getAnswer(),i.getCreatedAt(),i.getAnsweredAt());
    }
    @Override public Page<AuditView> auditLogs(AuditFilter f) {
        var p=new LinkedHashMap<String,Object>();
        String where=" where 1=1"+eq(p,"actor",f.actorId(),"a.actorId")+eq(p,"action",f.action(),"a.action")
                +eq(p,"type",f.targetType(),"a.targetType")+eq(p,"target",f.targetId(),"a.targetId");
        if(f.from()!=null){p.put("from",f.from());where+=" and a.createdAt>=:from";}
        if(f.to()!=null){p.put("to",f.to());where+=" and a.createdAt<=:to";}
        return page("AdminAuditLog","a",where,"a.createdAt desc,a.id desc",p,new Query(null,f.page(),f.size()),AdminAuditLog.class,
                a -> new AuditView(a.getId(),a.getActorId(),a.getAction(),a.getTargetType(),a.getTargetId(),a.getReason(),a.getChangedFields(),a.getOutcome(),a.getCorrelationId(),a.getCreatedAt()));
    }
    @Override public void audit(AdminAuditLog log) { em.persist(log); }
    @Override public void flush() { em.flush(); }

    private <E,T> Page<T> page(String entity,String alias,String where,String order,Map<String,Object> p,
                             Query f,Class<E> type,Function<E,T> mapper) {
        var list=em.createQuery("select "+alias+" from "+entity+" "+alias+where+" order by "+order,type);
        var count=em.createQuery("select count("+alias+") from "+entity+" "+alias+where,Long.class);
        p.forEach((key,value)->{list.setParameter(key,value);count.setParameter(key,value);});
        return new Page<>(list.setFirstResult(Math.multiplyExact(f.page(),f.size())).setMaxResults(f.size()).getResultList().stream().map(mapper).toList(),
                f.page(),f.size(),count.getSingleResult());
    }
    private static String eq(Map<String,Object> p,String key,Object value,String field) {
        if(value==null)return "";p.put(key,value);return " and "+field+"=:"+key;
    }
    private static String keyword(Query q,Map<String,Object> p,String...fields) {
        if(q.keyword()==null || q.keyword().isBlank())return "";
        // Literal LIKE search: input wildcard characters do not expand the search.
        p.put("keyword","%"+q.keyword().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        return " and ("+String.join(" or ",Arrays.stream(fields).map(f -> "lower("+f+") like :keyword escape '!' ").toList())+")";
    }
    private static BaseException missing() { return new BaseException(ErrorCode.RESOURCE_NOT_FOUND); }
}
