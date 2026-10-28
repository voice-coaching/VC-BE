package org.example.voice.payment.domain.model;

import org.example.voice.payment.domain.entity.PaymentCancellation;
import org.example.voice.payment.domain.type.PaymentCancellationStatus;

import java.time.OffsetDateTime;

public record PaymentCancellationData(String id, String orderId, PaymentCancellationStatus status,
                                      OffsetDateTime requestedAt, OffsetDateTime updatedAt) {
    public static PaymentCancellationData from(PaymentCancellation request) {
        return new PaymentCancellationData(request.getId(), request.getOrderId(), request.getStatus(),
                request.getRequestedAt(), request.getUpdatedAt());
    }
}
