package org.example.voice.payment.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.payment.domain.PaymentException;
import org.example.voice.payment.domain.model.PaymentCancellationData;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentRefundService {
    private final PaymentCancellationService cancellations;
    private final PaymentCancellationTransactions requests;

    public PaymentCancellationData request(Long userId, String orderId, String reason) {
        var normalized = PaymentCancellationService.normalizeReason(reason);

        // Refund and cancellation are one PG operation. A retry only retrieves the durable request.
        return requests.existingRequest(userId, orderId, normalized)
                .orElseGet(() -> cancellations.cancel(userId, orderId, normalized));
    }

    public PaymentCancellationData read(Long userId, String requestId) {

        return requests.read(userId, requestId);
    }
}
