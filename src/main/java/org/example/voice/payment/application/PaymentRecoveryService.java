package org.example.voice.payment.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.payment.domain.PaymentException;
import org.example.voice.payment.domain.PaymentPolicy;
import org.springframework.stereotype.Service;

/** Bounded, rotating recovery. Provider calls use the same validation path as manual verification. */
@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {
    public static final int BATCH_SIZE = 10;
    private final PaymentTransactions transactions;
    private final PaymentService payments;
    private final PaymentPolicy policy;
    private String afterId = "";

    public synchronized Result recover() {
        var candidates = transactions.recoveryCandidates(afterId, BATCH_SIZE);
        if (candidates.isEmpty() && !afterId.isEmpty()) {
            afterId = "";
            candidates = transactions.recoveryCandidates(afterId, BATCH_SIZE);
        }
        int applied = 0, missing = 0, failed = 0, skipped = 0;
        for (var candidate : candidates) {
            // Advance on failure too: one unavailable payment must not starve later orders.
            afterId = candidate.orderId();
            if (!policy.eligible(candidate.userId())) { skipped++; continue; }
            try {
                payments.verify(candidate.userId(), candidate.orderId());
                applied++;
            } catch (PaymentException e) {
                if ("PAYMENT_NOT_COMPLETED".equals(e.code())) missing++;
                else failed++;
            } catch (RuntimeException e) {
                // Do not expose provider payloads, credentials or customer details in logs.
                failed++;
            }
        }
        return new Result(applied, missing, failed, skipped);
    }

    public record Result(int applied, int missing, int failed, int skipped) {}
}
