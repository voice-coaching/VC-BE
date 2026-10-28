package org.example.voice.payment.infrastructure;

import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.example.voice.payment.domain.*;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.Optional;

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
}
