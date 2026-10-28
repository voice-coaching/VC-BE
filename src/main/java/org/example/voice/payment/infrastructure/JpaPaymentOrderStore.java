package org.example.voice.payment.infrastructure;

import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.example.voice.payment.domain.*;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class JpaPaymentOrderStore implements PaymentOrderStore {
    private final EntityManager em;
    @Override public Optional<PaymentOrder> find(String id) { return Optional.ofNullable(em.find(PaymentOrder.class, id)); }
    @Override public Optional<PaymentOrder> lock(String id) {
        return Optional.ofNullable(em.find(PaymentOrder.class, id, LockModeType.PESSIMISTIC_WRITE));
    }
    @Override public Optional<PaymentOrder> findByKey(Long userId, String key) {
        return em.createQuery("select p from PaymentOrder p where p.userId=:user and p.idempotencyKey=:key", PaymentOrder.class)
                .setParameter("user", userId).setParameter("key", key).getResultStream().findFirst();
    }
    @Override public PaymentOrder save(PaymentOrder order) {
        if (!em.contains(order)) em.persist(order);
        return order;
    }
    @Override public PaymentHistory history(Long userId, int page, int size) {
        long total = em.createQuery("select count(p) from PaymentOrder p where p.userId=:user", Long.class)
                .setParameter("user", userId).getSingleResult();
        long offset = (long) page * size;
        if (offset >= total) return new PaymentHistory(List.of(), page, size, total);
        var items = em.createQuery("select p from PaymentOrder p where p.userId=:user order by p.createdAt desc, p.id desc", PaymentOrder.class)
                .setParameter("user", userId).setFirstResult(Math.toIntExact(offset)).setMaxResults(size)
                .getResultStream().map(p -> new PaymentHistory.Item(p.getId(), p.getProduct(), p.getAmount(),
                        p.getCurrency(), p.getStatus(), p.getCreatedAt(), p.getPaidAt(), p.getExpiresAt())).toList();
        return new PaymentHistory(items, page, size, total);
    }
    @Override public Optional<OffsetDateTime> activeUntil(Long userId, OffsetDateTime now) {
        return em.createQuery("select p.expiresAt from PaymentOrder p where p.userId=:user and p.status='PAID' and p.paidAt<=:now and p.expiresAt>:now order by p.expiresAt desc", OffsetDateTime.class)
                .setParameter("user", userId).setParameter("now", now).setMaxResults(1).getResultStream().findFirst();
    }
    @Override public boolean hasReceipt(String eventId, String hash) {
        var receipt = em.find(PaymentWebhookReceipt.class, eventId);
        if (receipt == null) return false;
        if (!receipt.payloadHash.equals(hash)) throw new PaymentException(409, "WEBHOOK_EVENT_CONFLICT");
        return true;
    }
    @Override public void receipt(String eventId, String hash, OffsetDateTime now) {
        em.persist(new PaymentWebhookReceipt(eventId, hash, now));
    }
    @Override public List<RecoveryCandidate> recoveryCandidates(String afterId, OffsetDateTime now, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid recovery batch size");
        return em.createQuery("select p from PaymentOrder p where p.id>:after and "
                        + "(p.status='PENDING' or (p.status='PAID' and p.expiresAt>:now)) order by p.id", PaymentOrder.class)
                .setParameter("after", afterId).setParameter("now", now).setMaxResults(limit)
                .getResultStream().map(p -> new RecoveryCandidate(p.getId(), p.getUserId())).toList();
    }
}
