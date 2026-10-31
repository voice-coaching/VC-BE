package org.example.voice.payment.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.payment.domain.PaymentOperation;
import org.example.voice.payment.domain.port.PaymentRequestQuota;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentQuotaTransactions {
    private final PaymentRequestQuota quota;

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 3)
    public long consume(Long userId, PaymentOperation operation) {
        return quota.consume(userId, operation);
    }
}
