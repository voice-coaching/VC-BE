package org.example.voice.payment.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.example.voice.payment.domain.PaymentOperation;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "payment_request_quotas", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "operation"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentQuota {
    @Id @Column(length = 64) private String id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private PaymentOperation operation;
    @Column(name = "window_started_at", nullable = false) private Instant windowStartedAt;
    @Column(nullable = false) private int attempts;

    public PaymentQuota(Long userId, PaymentOperation operation, Instant now) {
        this.id = id(userId, operation);
        this.userId = userId;
        this.operation = operation;
        this.windowStartedAt = now;
    }

    public static String id(Long userId, PaymentOperation operation) { return userId + ":" + operation.name(); }

    public long consume(Instant now) {
        Instant resetAt = windowStartedAt.plusSeconds(60);
        if (!now.isBefore(resetAt)) {
            windowStartedAt = now;
            attempts = 0;
            resetAt = now.plusSeconds(60);
        }
        if (attempts >= operation.limit()) {
            Duration remaining = Duration.between(now, resetAt);
            return remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
        }
        attempts++;
        return 0;
    }
}
