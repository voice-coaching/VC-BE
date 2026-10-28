package org.example.voice.payment.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.payment.domain.PaymentException;
import org.example.voice.payment.domain.PaymentGateway;
import org.example.voice.payment.domain.PaymentSnapshot;
import org.example.voice.payment.domain.model.PaymentCancellationData;
import org.example.voice.payment.domain.type.PaymentCancellationStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentCancellationService {
    private final PaymentTransactions payments;
    private final PaymentCancellationTransactions cancellations;
    private final PaymentGateway gateway;

    public PaymentCancellationData cancel(Long userId, String orderId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 200
                || reason.codePoints().anyMatch(Character::isISOControl))
            throw new PaymentException(400, "INVALID_CANCELLATION_REASON");
        // Ownership and feature access are checked before any provider request.
        var order = payments.owned(userId, orderId);
        cancellations.assertActive(userId);
        var snapshot = gateway.get(order.getId());
        payments.apply(userId, orderId, snapshot, null, null);
        boolean cancelled = fullyCancelled(snapshot);
        if (!cancelled && (!"PAID".equals(snapshot.status()) || snapshot.cancelled() != 0))
            throw new PaymentException(409, "PAYMENT_NOT_CANCELLABLE");
        var reservation = cancellations.reserve(userId, orderId, reason.strip(), cancelled);
        if (!reservation.send()) return reservation.request();
        try {
            var result = gateway.cancel(orderId, reservation.request().id(), reason.strip(), order.getAmount());
            var verified = gateway.get(orderId);
            payments.apply(userId, orderId, verified, null, null);
            var status = fullyCancelled(verified) ? PaymentCancellationStatus.SUCCEEDED
                    : "FAILED".equals(result) ? PaymentCancellationStatus.FAILED : PaymentCancellationStatus.PENDING;
            return cancellations.record(userId, orderId, status);
        } catch (RuntimeException error) {
            // The request was committed before POST. Never resend on an ambiguous outcome.
            // Existing verify/webhook/recovery paths can subsequently reconcile a full cancellation.
            cancellations.record(userId, orderId, PaymentCancellationStatus.UNKNOWN);
            throw new PaymentException(502, "PAYMENT_CANCELLATION_UNCONFIRMED");
        }
    }

    private boolean fullyCancelled(PaymentSnapshot snapshot) {
        return "CANCELLED".equals(snapshot.status()) && snapshot.cancelled() == snapshot.total();
    }
}
