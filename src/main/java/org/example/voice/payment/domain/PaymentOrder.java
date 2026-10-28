package org.example.voice.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_test_orders", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOrder {
    public static final long PRICE = 15000;
    public static final String PRODUCT = "SPEAKAI_MONTHLY_TEST_V1";
    @Id @Column(length = 64) private String id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "idempotency_key", nullable = false, length = 64) private String idempotencyKey;
    @Column(nullable = false, length = 40) private String product;
    @Column(nullable = false) private long amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, length = 24) private String status;
    @Column(nullable = false) private OffsetDateTime createdAt;
    private OffsetDateTime paidAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime providerUpdatedAt;
    @Version private long version;

    public static PaymentOrder create(Long userId, String key, OffsetDateTime now) {
        var order = new PaymentOrder();
        // KCP allows at most 40 characters. ASCII alphanumeric, 36 characters total.
        order.id = "test" + UUID.randomUUID().toString().replace("-", "");
        order.userId = userId; order.idempotencyKey = key; order.product = PRODUCT;
        order.amount = PRICE; order.currency = "KRW"; order.status = "PENDING"; order.createdAt = now;
        return order;
    }

    public void apply(PaymentSnapshot payment, OffsetDateTime now) {
        if (!id.equals(payment.id()) || amount != payment.total() || !currency.equals(payment.currency()))
            throw new PaymentException(409, "PAYMENT_MISMATCH");
        if (payment.updatedAt() == null || payment.updatedAt().isAfter(now.plusMinutes(5)))
            throw new PaymentException(409, "PAYMENT_TIMESTAMP_INVALID");
        // Cancellation is terminal. A delayed paid response must never restore access.
        if ("CANCELLED".equals(status)) return;
        // A cancellation for this immutable payment id always wins, including equal provider
        // timestamps or a delayed cancellation notification from a lagging read replica.
        if ("CANCELLED".equals(payment.status()) || "PARTIAL_CANCELLED".equals(payment.status()) || payment.cancelled() > 0) {
            status = "CANCELLED";
            if (providerUpdatedAt == null || payment.updatedAt().isAfter(providerUpdatedAt)) providerUpdatedAt = payment.updatedAt();
            return;
        }
        if (providerUpdatedAt != null && !payment.updatedAt().isAfter(providerUpdatedAt)) return;
        switch (payment.status()) {
            case "PAID" -> {
                if (payment.paidAt() == null || payment.paidAt().isBefore(createdAt.minusMinutes(5))
                        || payment.paidAt().isAfter(now.plusMinutes(5)))
                    throw new PaymentException(409, "PAYMENT_TIMESTAMP_INVALID");
                if (paidAt == null) {
                    paidAt = payment.paidAt();
                    // TEST policy: one calendar month in UTC; retries never extend it.
                    expiresAt = paidAt.withOffsetSameInstant(java.time.ZoneOffset.UTC).plusMonths(1);
                }
                status = "PAID";
            }
            case "FAILED" -> { if (!"PAID".equals(status)) status = "FAILED"; }
            case "READY", "PAY_PENDING", "VIRTUAL_ACCOUNT_ISSUED" -> { /* no entitlement */ }
            default -> throw new PaymentException(409, "PAYMENT_STATUS_UNSUPPORTED");
        }
        providerUpdatedAt = payment.updatedAt();
    }
}
