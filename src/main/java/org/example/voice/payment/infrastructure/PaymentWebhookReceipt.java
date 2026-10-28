package org.example.voice.payment.infrastructure;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payment_test_webhook_receipts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PaymentWebhookReceipt {
    @Id @Column(length = 200) String id;
    @Column(nullable = false, length = 64) String payloadHash;
    @Column(nullable = false) OffsetDateTime processedAt;
    PaymentWebhookReceipt(String id, String hash, OffsetDateTime now) {
        this.id = id; this.payloadHash = hash; this.processedAt = now;
    }
}
