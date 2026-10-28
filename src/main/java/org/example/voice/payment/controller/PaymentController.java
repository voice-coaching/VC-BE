package org.example.voice.payment.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.payment.application.*;
import org.example.voice.payment.domain.*;
import org.example.voice.payment.controller.dto.PaymentHistoryResponse;
import org.example.voice.payment.controller.dto.PaymentCancellationRequestDto;
import org.example.voice.payment.controller.dto.PaymentCancellationResponseDto;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    private final PaymentHistoryService history;
    private final PaymentCancellationService cancellations;

    @PostMapping("/api/payment-orders/{orderId}/cancel")
    public ApiResponse<PaymentCancellationResponseDto> cancel(@AuthenticationPrincipal LoginUser user,
            @PathVariable String orderId, @RequestBody PaymentCancellationRequestDto request) {
        return ApiResponse.success("테스트 결제 취소 요청을 확인했습니다.",
                PaymentCancellationResponseDto.from(cancellations.cancel(user.id(), orderId, request.reason())));
    }

    @GetMapping("/api/users/me/payment-orders")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: page는 0 이상, size는 1~100, page*size는 2147483647 이하의 정수"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED: 로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "TEST_PAYMENT_DISABLED: 테스트 결제 비활성 또는 비허용 사용자")
    })
    public ApiResponse<PaymentHistoryResponse> history(@AuthenticationPrincipal LoginUser user,
            @Parameter(schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0")) @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("결제 내역을 조회했습니다.", PaymentHistoryResponse.from(history.read(user.id(), page, size)));
    }

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
