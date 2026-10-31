package org.example.voice.payment.domain;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;

public interface PaymentOrderStore {
    Optional<PaymentOrder> find(String id);
    Optional<PaymentOrder> lock(String id);
    Optional<PaymentOrder> findByKey(Long userId, String key);
    PaymentOrder save(PaymentOrder order);
    PaymentHistory history(Long userId, int page, int size);
    Optional<OffsetDateTime> activeUntil(Long userId, OffsetDateTime now);
    boolean hasReceipt(String eventId, String hash);
    void receipt(String eventId, String hash, OffsetDateTime now);
    /** Keyset page of pending orders and unexpired paid grants; no provider I/O. */
    List<RecoveryCandidate> recoveryCandidates(String afterId, OffsetDateTime now, int limit);
    record RecoveryCandidate(String orderId, Long userId) {}
}
