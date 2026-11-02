package org.example.voice.payment.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.payment.domain.*;
import org.example.voice.user.domain.port.UserReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/** All writes lock the user first, then the order. No provider calls inside these transactions. */
@Service
@RequiredArgsConstructor
public class PaymentTransactions {
    private final PaymentOrderStore orders;
    private final UserReader users;
    private final PaymentPolicy policy;
    private final Clock clock;
    private final org.example.voice.payment.domain.port.PaymentCancellationStore cancellations;

    @Transactional
    public PaymentOrder create(Long userId, String key) {

        activeUser(userId);
        var existing = orders.findByKey(userId, key);

        var now = OffsetDateTime.now(clock);

        return orders.save(PaymentOrder.create(userId, key, now));
    }

    @Transactional(readOnly = true)
    public PaymentOrder owned(Long userId, String id) {

        var order = orders.find(id).orElseThrow(() -> new PaymentException(404, "PAYMENT_ORDER_NOT_FOUND"));

        return order;
    }

    @Transactional(readOnly = true)
    public PaymentOrder findForWebhook(String id) { return orders.find(id).orElse(null); }

    @Transactional(readOnly = true)
    public List<PaymentOrderStore.RecoveryCandidate> recoveryCandidates(String afterId, int limit) {
        return orders.recoveryCandidates(afterId, OffsetDateTime.now(clock), limit);
    }

    @Transactional
    public PaymentOrder apply(Long userId, String id, PaymentSnapshot snapshot, String eventId, String hash) {

        // Lock even a withdrawn user: record refunds safely, but membership queries exclude inactive users.
        users.findByIdForUpdate(userId).orElseThrow(() -> new PaymentException(404, "PAYMENT_ORDER_NOT_FOUND"));
        var order = orders.lock(id).orElseThrow(() -> new PaymentException(404, "PAYMENT_ORDER_NOT_FOUND"));

        var now = OffsetDateTime.now(clock);
        order.apply(snapshot, now);
        orders.save(order);
        if ("CANCELLED".equals(snapshot.status()) && snapshot.cancelled() == snapshot.total()) {
            cancellations.byOrder(id).ifPresent(request -> request.record(
                    org.example.voice.payment.domain.type.PaymentCancellationStatus.SUCCEEDED, now));
        }
        if (eventId != null) orders.receipt(eventId, hash, now);
        return order;
    }

    private void activeUser(Long userId) {
        var user = users.findByIdForUpdate(userId).orElseThrow(() -> new PaymentException(403, "PAYMENT_USER_INACTIVE"));

    }
}
