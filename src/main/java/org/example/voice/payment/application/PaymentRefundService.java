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
        requests.assertActive(userId);
        // Refund and cancellation are one PG operation. A retry only retrieves the durable request.
        return requests.existingRequest(userId, orderId, normalized)
                .orElseGet(() -> cancellations.cancel(userId, orderId, normalized));
    }

    public PaymentCancellationData read(Long userId, String requestId) {
        if (requestId == null || !requestId.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
            throw new PaymentException(404, "PAYMENT_CANCELLATION_NOT_FOUND");
        return requests.read(userId, requestId);
    }
}
