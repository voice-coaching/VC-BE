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

    public void require(Long userId, PaymentOperation operation) {}
}
