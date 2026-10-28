package org.example.voice.payment.domain.port;

import org.example.voice.payment.domain.entity.PaymentCancellation;

import java.util.Optional;

public interface PaymentCancellationStore {
    Optional<PaymentCancellation> byOrder(String orderId);
    Optional<PaymentCancellation> byId(String requestId);
    PaymentCancellation save(PaymentCancellation request);
}
