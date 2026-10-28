package org.example.voice.payment.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.payment.application.*;
import org.example.voice.payment.domain.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

@RestController
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentTransactions transactions;
    private final PaymentService service;
    private final PaymentPolicy policy;

    @PostMapping("/api/payment-orders")
    public ApiResponse<OrderResponse> create(@AuthenticationPrincipal LoginUser user,
            @RequestHeader("Idempotency-Key") String key) {
        if (!key.matches("[A-Za-z0-9_-]{16,64}")) throw new PaymentException(400, "INVALID_IDEMPOTENCY_KEY");
        return ApiResponse.success("테스트 주문을 생성했습니다.", response(transactions.create(user.id(), key)));
    }
    @GetMapping("/api/payment-orders/{orderId}")
    public ApiResponse<OrderResponse> get(@AuthenticationPrincipal LoginUser user, @PathVariable String orderId) {
        return ApiResponse.success("테스트 주문을 조회했습니다.", response(transactions.owned(user.id(), orderId)));
    }
    @PostMapping("/api/payment-orders/{orderId}/verify")
    public ApiResponse<OrderResponse> verify(@AuthenticationPrincipal LoginUser user, @PathVariable String orderId) {
        return ApiResponse.success("포트원 결제 상태를 확인했습니다.", response(service.verify(user.id(), orderId)));
    }
    @PostMapping("/api/payments/webhooks/portone")
    public ApiResponse<Void> webhook(HttpServletRequest request) throws IOException {
        var body = request.getInputStream().readNBytes(65537);
        if (body.length > 65536) throw new PaymentException(413, "WEBHOOK_BODY_TOO_LARGE");
        service.webhook(new String(body, StandardCharsets.UTF_8), request.getHeader("webhook-id"),
                request.getHeader("webhook-signature"), request.getHeader("webhook-timestamp"));
        return ApiResponse.success("웹훅을 처리했습니다.");
    }
    private OrderResponse response(PaymentOrder order) {
        var checkout = policy.checkout();
        return new OrderResponse(order.getId(), order.getProduct(), "SPEAK AI 월간 이용권 (테스트)", order.getAmount(),
                order.getCurrency(), order.getStatus(), "TEST", order.getExpiresAt(), checkout.storeId(), checkout.channelKey());
    }
    public record OrderResponse(String orderId, String product, String orderName, long totalAmount, String currency,
                                String status, String environment, OffsetDateTime expiresAt, String storeId, String channelKey) {}
}
