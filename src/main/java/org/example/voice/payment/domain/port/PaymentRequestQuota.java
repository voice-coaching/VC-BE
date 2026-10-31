package org.example.voice.payment.domain.port;

import org.example.voice.payment.domain.PaymentOperation;

public interface PaymentRequestQuota {
    /** Returns zero when consumed, otherwise the positive number of seconds until retry. */
    long consume(Long userId, PaymentOperation operation);
}
