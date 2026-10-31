package org.example.voice.payment.domain;

import java.time.OffsetDateTime;
import java.util.List;

/** Read model without checkout credentials, idempotency keys or customer data. */
public record PaymentHistory(List<Item> items, int page, int size, long totalElements) {
    public PaymentHistory { items = List.copyOf(items); }
    public long totalPages() { return totalElements / size + (totalElements % size == 0 ? 0 : 1); }
    public boolean hasNext() { return ((long) page + 1) * size < totalElements; }

    public record Item(String orderId, String product, long totalAmount, String currency, String status,
                       OffsetDateTime createdAt, OffsetDateTime paidAt, OffsetDateTime expiresAt) {}
}
