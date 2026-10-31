package org.example.voice.payment.domain;

public interface PaymentGateway {
    PaymentSnapshot get(String paymentId);
    String cancel(String paymentId, String requestId, String reason, long amount);
}
