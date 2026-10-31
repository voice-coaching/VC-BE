package org.example.voice.payment.controller.dto;

import org.example.voice.payment.domain.PaymentHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record PaymentHistoryResponse(List<Item> items, int page, int size, long totalElements,
                                     long totalPages, boolean hasNext) {
    public static PaymentHistoryResponse from(PaymentHistory history) {
        return new PaymentHistoryResponse(history.items().stream().map(Item::from).toList(), history.page(),
                history.size(), history.totalElements(), history.totalPages(), history.hasNext());
    }
    @Schema(name = "PaymentHistoryItem")
    public record Item(String orderId, String product, String orderName, long totalAmount, String currency,
                       String status, String environment, OffsetDateTime createdAt, OffsetDateTime paidAt,
                       OffsetDateTime expiresAt) {
        static Item from(PaymentHistory.Item item) {
            return new Item(item.orderId(), item.product(), "SPEAK AI 월간 이용권 (테스트)", item.totalAmount(),
                    item.currency(), item.status(), "TEST", item.createdAt(), item.paidAt(), item.expiresAt());
        }
    }
}
