package org.example.voice.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_test_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOrder {
    public static final long PRICE = 15000;
    public static final String PRODUCT = "SPEAKAI_MONTHLY_TEST_V1";
    @Id @Column(length = 64) private String id;
    @Column(name = "user_id", nullable = true) private Long userId;
    @Column(name = "idempotency_key", nullable = true, length = 64) private String idempotencyKey;
    @Column(nullable = true, length = 40) private String product;
    @Column(nullable = true) private long amount;
    @Column(nullable = true, length = 3) private String currency;
    @Column(nullable = true, length = 24) private String status;
    @Column(nullable = true) private OffsetDateTime createdAt;
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
        providerUpdatedAt=payment.updatedAt();
        status=payment.status();
        if ("PAID".equals(payment.status())) {
            paidAt=payment.paidAt();
            expiresAt=paidAt == null ? null : paidAt.withOffsetSameInstant(java.time.ZoneOffset.UTC).plusMonths(1);
        }
    }
}
