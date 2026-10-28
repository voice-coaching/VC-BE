package org.example.voice.payment.domain.port;

import org.example.voice.payment.domain.entity.PaymentCancellation;

import java.util.Optional;

public interface PaymentCancellationStore {
    Optional<PaymentCancellation> byOrder(String orderId);
    PaymentCancellation save(PaymentCancellation request);
}
