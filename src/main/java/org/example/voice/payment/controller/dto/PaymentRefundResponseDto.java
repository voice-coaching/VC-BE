package org.example.voice.payment.controller.dto;

import org.example.voice.payment.domain.PaymentOrder;
import org.example.voice.payment.domain.model.PaymentCancellationData;
import org.example.voice.payment.domain.type.PaymentCancellationStatus;

import java.time.OffsetDateTime;

public record PaymentRefundResponseDto(String requestId, String orderId, PaymentCancellationStatus status,
                                       long amount, String currency, String environment,
                                       OffsetDateTime requestedAt, OffsetDateTime updatedAt) {
    public static PaymentRefundResponseDto from(PaymentCancellationData data) {
        return new PaymentRefundResponseDto(data.id(), data.orderId(), data.status(), PaymentOrder.PRICE,
                "KRW", "TEST", data.requestedAt(), data.updatedAt());
    }
}
