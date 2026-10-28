package org.example.voice.payment.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.voice.payment.domain.entity.PaymentCancellation;
import org.example.voice.payment.domain.port.PaymentCancellationStore;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentCancellationStoreImpl implements PaymentCancellationStore {
    private final EntityManager em;

    @Override
    public Optional<PaymentCancellation> byOrder(String orderId) {
        return em.createQuery("select c from PaymentCancellation c where c.orderId=:order", PaymentCancellation.class)
                .setParameter("order", orderId).getResultStream().findFirst();
    }

    @Override
    public PaymentCancellation save(PaymentCancellation request) {
        if (!em.contains(request)) em.persist(request);
        return request;
    }

    @Override
    public Optional<PaymentCancellation> byId(String requestId) {
        return Optional.ofNullable(em.find(PaymentCancellation.class, requestId));
    }
}
