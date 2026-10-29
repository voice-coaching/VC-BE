package org.example.voice.payment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice.payment.domain.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRequestLimiter {
    private final PaymentQuotaTransactions transactions;
    private final PaymentPolicy policy;

    public void require(Long userId, PaymentOperation operation) {
        policy.require(userId);
        final long retryAfter;
        try {
            // Commit the attempt before business work: a failed payment must not refund its quota.
            retryAfter = transactions.consume(userId, operation);
        } catch (RuntimeException unavailable) {
            log.warn("Payment request quota unavailable");
            throw new PaymentException(503, "PAYMENT_RATE_LIMIT_UNAVAILABLE");
        }
        if (retryAfter > 0) throw new PaymentRateLimitedException(retryAfter);
    }
}
