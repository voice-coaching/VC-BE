package org.example.voice.payment.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.payment.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentHistoryService {
    private final PaymentOrderStore orders;
    private final PaymentPolicy policy;

    @Transactional(readOnly = true)
    public PaymentHistory read(Long userId, int page, int size) {
        policy.require(userId);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new PaymentException(400, "VALIDATION_ERROR");
        return orders.history(userId, page, size);
    }
}
