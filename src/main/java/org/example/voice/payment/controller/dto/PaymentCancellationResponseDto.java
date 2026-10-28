package org.example.voice.payment.controller.dto;

import org.example.voice.payment.domain.model.PaymentCancellationData;
import org.example.voice.payment.domain.type.PaymentCancellationStatus;

import java.time.OffsetDateTime;

public record PaymentCancellationResponseDto(String requestId, String orderId, PaymentCancellationStatus status,
                                             OffsetDateTime requestedAt, OffsetDateTime updatedAt) {
    public static PaymentCancellationResponseDto from(PaymentCancellationData data) {
        return new PaymentCancellationResponseDto(data.id(), data.orderId(), data.status(),
                data.requestedAt(), data.updatedAt());
    }
}
