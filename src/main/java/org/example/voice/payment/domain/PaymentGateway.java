package org.example.voice.payment.domain;

public interface PaymentGateway {
    PaymentSnapshot get(String paymentId);
}
