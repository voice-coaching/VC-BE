package org.example.voice.payment.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.payment.application.PaymentRefundService;
import org.example.voice.payment.application.PaymentRequestLimiter;
import org.example.voice.payment.domain.PaymentOperation;
import org.example.voice.payment.controller.dto.PaymentRefundRequestDto;
import org.example.voice.payment.controller.dto.PaymentRefundResponseDto;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentRefundController {
    private final PaymentRefundService refunds;
    private final PaymentRequestLimiter limiter;

    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "PAYMENT_RATE_LIMITED: 회원별 작업 한도 초과. Retry-After 초 이후 재시도",
                headers = @io.swagger.v3.oas.annotations.headers.Header(name = "Retry-After", description = "재시도까지 남은 초",
                        schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", format = "int64", minimum = "1"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "PAYMENT_RATE_LIMIT_UNAVAILABLE: 호출 제한 저장소 오류. 결제 처리 미실행")
    })
    @PostMapping("/api/payment-orders/{orderId}/refund-requests")
    public ResponseEntity<ApiResponse<PaymentRefundResponseDto>> request(@AuthenticationPrincipal LoginUser user,
            @PathVariable String orderId, @RequestBody PaymentRefundRequestDto request) {
        limiter.require(user.id(), PaymentOperation.CANCEL);
        var result = PaymentRefundResponseDto.from(refunds.request(user.id(), orderId, request.reason()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("전액 환불 신청을 확인했습니다.", result));
    }

    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "PAYMENT_RATE_LIMITED: 회원별 작업 한도 초과. Retry-After 초 이후 재시도",
                headers = @io.swagger.v3.oas.annotations.headers.Header(name = "Retry-After", description = "재시도까지 남은 초",
                        schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", format = "int64", minimum = "1"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "PAYMENT_RATE_LIMIT_UNAVAILABLE: 호출 제한 저장소 오류. 결제 처리 미실행")
    })
    @GetMapping("/api/refund-requests/{requestId}")
    public ResponseEntity<ApiResponse<PaymentRefundResponseDto>> read(@AuthenticationPrincipal LoginUser user,
            @PathVariable String requestId) {
        limiter.require(user.id(), PaymentOperation.READ);
        var result = PaymentRefundResponseDto.from(refunds.read(user.id(), requestId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("전액 환불 처리 상태를 조회했습니다.", result));
    }
}
