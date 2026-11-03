package org.example.voice.admin.infrastructure;

import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.*;
import org.example.voice.admin.domain.entity.*;
import org.example.voice.admin.domain.model.AdminData.Page;
import org.example.voice.admin.domain.port.AdminCommunicationStore;

@Repository @RequiredArgsConstructor
public class AdminCommunicationPersistence implements AdminCommunicationStore {
    private final EntityManager em;
    public void save(AdminNotificationCampaign c) { em.persist(c); }
    public Optional<AdminNotificationCampaign> campaign(long id,boolean lock) {
        return Optional.ofNullable(em.find(AdminNotificationCampaign.class,id,lock?LockModeType.PESSIMISTIC_WRITE:LockModeType.NONE));
    }
    public Page<AdminNotificationCampaign> campaigns(int page,int size) {
        return new Page<>(em.createQuery("select c from AdminNotificationCampaign c order by c.id desc",AdminNotificationCampaign.class)
                .setFirstResult(page*size).setMaxResults(size).getResultList(),page,size,
                em.createQuery("select count(c) from AdminNotificationCampaign c",Long.class).getSingleResult());
    }
    public long activeRecipients(Set<Long> ids,boolean all) {
        if(!all&&ids.isEmpty())return 0;
        var q=em.createQuery("select count(u) from User u where u.status=org.example.voice.user.domain.type.UserStatus.ACTIVE "
                +"and u.deletedAt is null"+(all?"":" and u.id in :ids"),Long.class);
        if(!all)q.setParameter("ids",ids);return q.getSingleResult();
    }
    public int deliver(AdminNotificationCampaign c,OffsetDateTime now) {
        // One statement and the campaign row lock keep delivery, state and audit in the same transaction.
        String audience=c.getAudience()==org.example.voice.admin.domain.type.AdminAudience.ALL_ACTIVE?"":
                " and exists (select 1 from admin_notification_recipients r where r.campaign_id=:campaign and r.user_id=u.id)";
        var q=em.createNativeQuery("insert into user_notifications (user_id,type,title,body,deep_link,deduplication_key,created_at) "
                +"select u.id,:type,:title,:body,:link,:dedup,:now from users u where u.status='ACTIVE' and u.deleted_at is null"+audience)
                .setParameter("type","ADMIN_"+c.getKind().name()).setParameter("title",c.getTitle()).setParameter("body",c.getBody())
                .setParameter("link",c.getDeepLink()).setParameter("dedup","admin-campaign:"+c.getId()).setParameter("now",now);
        if(!audience.isEmpty())q.setParameter("campaign",c.getId());
        em.flush();return q.executeUpdate();
    }
    public void save(AdminPopup p) { em.persist(p); }
    public Optional<AdminPopup> popup(long id,boolean lock) {
        return Optional.ofNullable(em.find(AdminPopup.class,id,lock?LockModeType.PESSIMISTIC_WRITE:LockModeType.NONE));
    }
    public Page<AdminPopup> popups(int page,int size) {
        return new Page<>(em.createQuery("select p from AdminPopup p order by p.id desc",AdminPopup.class)
                .setFirstResult(page*size).setMaxResults(size).getResultList(),page,size,
                em.createQuery("select count(p) from AdminPopup p",Long.class).getSingleResult());
    }
    public Optional<AdminPopup> activePopup(long userId,OffsetDateTime now) {
        return em.createQuery("select p from AdminPopup p where p.enabled=true and p.startsAt<=:now and p.endsAt>:now "
                +"and exists (select u.id from User u where u.id=:user and u.status=org.example.voice.user.domain.type.UserStatus.ACTIVE and u.deletedAt is null) "
                +"order by p.priority desc,p.startsAt desc,p.id desc",AdminPopup.class)
                .setParameter("now",now).setParameter("user",userId).setMaxResults(1).getResultStream().findFirst();
    }
}
