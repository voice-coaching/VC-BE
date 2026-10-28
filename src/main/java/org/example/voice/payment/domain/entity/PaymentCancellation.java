package org.example.voice.payment.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.payment.domain.type.PaymentCancellationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_test_cancellations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancellation {
    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;
    @Column(nullable = false, length = 200)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentCancellationStatus status;
    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static PaymentCancellation create(String orderId, String reason, OffsetDateTime now) {
        var request = new PaymentCancellation();
        request.id = UUID.randomUUID().toString();
        request.orderId = orderId;
        request.reason = reason;
        // Commit before sending money-moving I/O. A crash must never allow a second POST.
        request.status = PaymentCancellationStatus.UNKNOWN;
        request.requestedAt = now;
        request.updatedAt = now;
        return request;
    }

    public void record(PaymentCancellationStatus status, OffsetDateTime now) {
        if (this.status == PaymentCancellationStatus.SUCCEEDED) return;
        this.status = status;
        this.updatedAt = now;
    }
}
