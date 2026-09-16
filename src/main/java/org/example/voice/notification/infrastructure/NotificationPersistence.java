package org.example.voice.notification.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.voice.notification.domain.entity.*;
import org.example.voice.notification.domain.model.NotificationData.Page;
import org.example.voice.notification.domain.port.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Repository @RequiredArgsConstructor
public class NotificationPersistence implements NotificationStore, NotificationLifecycle {
    private final EntityManager em;
    public Optional<NotificationPreference> preferences(Long userId) { return Optional.ofNullable(em.find(NotificationPreference.class, userId)); }
    public void save(NotificationPreference value) { if (!em.contains(value)) em.persist(value); }
    public Page list(Long userId, boolean unreadOnly, int page, int size) {
        String where = " where n.userId=:user" + (unreadOnly ? " and n.readAt is null" : "");
        var items = em.createQuery("select n from UserNotification n" + where + " order by n.createdAt desc,n.id desc", UserNotification.class)
                .setParameter("user", userId).setFirstResult(page * size).setMaxResults(size).getResultList().stream().map(UserNotification::view).toList();
        long total = em.createQuery("select count(n) from UserNotification n" + where, Long.class).setParameter("user", userId).getSingleResult();
        long unread = em.createQuery("select count(n) from UserNotification n where n.userId=:user and n.readAt is null", Long.class).setParameter("user", userId).getSingleResult();
        return new Page(items, page, size, total, (int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size), ((long) page + 1) * size < total, unread);
    }
    public Optional<UserNotification> notification(Long userId, Long id) {
        return em.createQuery("select n from UserNotification n where n.userId=:user and n.id=:id", UserNotification.class)
                .setParameter("user", userId).setParameter("id", id).getResultStream().findFirst();
    }
    public int readAll(Long userId, OffsetDateTime now) {
        return em.createQuery("update UserNotification n set n.readAt=:now where n.userId=:user and n.readAt is null")
                .setParameter("now", now).setParameter("user", userId).executeUpdate();
    }
    public boolean hasEvent(Long userId, String key) {
        return em.createQuery("select count(n) from UserNotification n where n.userId=:user and n.deduplicationKey=:key", Long.class)
                .setParameter("user", userId).setParameter("key", key).getSingleResult() > 0;
    }
    public void save(UserNotification notification) { em.persist(notification); }
    @Transactional(readOnly = true)
    public List<Long> reminderUsers(Long after, int size) {
        return em.createQuery("select p.userId from NotificationPreference p where p.enabled=true and p.userId>:after order by p.userId", Long.class)
                .setParameter("after", after).setMaxResults(size).getResultList();
    }
    public Optional<PushSubscription> subscriptionByHash(String hash) {
        return em.createQuery("select s from PushSubscription s where s.endpointHash=:hash", PushSubscription.class)
                .setParameter("hash", hash).getResultStream().findFirst();
    }
    public Optional<PushSubscription> subscription(Long userId, Long id) {
        return em.createQuery("select s from PushSubscription s where s.userId=:user and s.id=:id", PushSubscription.class)
                .setParameter("user", userId).setParameter("id", id).getResultStream().findFirst();
    }
    public long subscriptionCount(Long userId) {
        return em.createQuery("select count(s) from PushSubscription s where s.userId=:user", Long.class).setParameter("user", userId).getSingleResult();
    }
    public PushSubscription save(PushSubscription subscription) { if (!em.contains(subscription)) em.persist(subscription); em.flush(); return subscription; }
    public void delete(PushSubscription subscription) { em.remove(subscription); }
    public Optional<PushRegistrationRequest> request(Long userId, String keyHash) {
        return em.createQuery("select r from PushRegistrationRequest r where r.userId=:user and r.keyHash=:key", PushRegistrationRequest.class)
                .setParameter("user", userId).setParameter("key", keyHash).getResultStream().findFirst();
    }
    public void save(PushRegistrationRequest request) { em.persist(request); }
    @Transactional
    public void eraseForUser(Long userId) {
        for (String entity : List.of("PushRegistrationRequest", "PushSubscription", "UserNotification", "NotificationPreference"))
            em.createQuery("delete from " + entity + " n where n.userId=:user").setParameter("user", userId).executeUpdate();
    }
    @Transactional
    public void recordPushResponse(Long id, OffsetDateTime attemptedRevision, int status) {
        if (status == 404 || status == 410)
            em.createQuery("update PushSubscription s set s.active=false where s.id=:id and s.updatedAt=:revision")
                    .setParameter("id", id).setParameter("revision", attemptedRevision).executeUpdate();
    }
}
