package org.example.voice.payment.domain;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface PaymentOrderStore {
    Optional<PaymentOrder> find(String id);
    Optional<PaymentOrder> lock(String id);
    Optional<PaymentOrder> findByKey(Long userId, String key);
    PaymentOrder save(PaymentOrder order);
    Optional<OffsetDateTime> activeUntil(Long userId, OffsetDateTime now);
    boolean hasReceipt(String eventId, String hash);
    void receipt(String eventId, String hash, OffsetDateTime now);
}
