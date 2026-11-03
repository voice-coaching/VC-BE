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

        return orders.history(userId, page, size);
    }
}
