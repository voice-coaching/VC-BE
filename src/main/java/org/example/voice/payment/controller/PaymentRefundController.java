package org.example.voice.payment.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.payment.application.PaymentRefundService;
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

    @PostMapping("/api/payment-orders/{orderId}/refund-requests")
    public ResponseEntity<ApiResponse<PaymentRefundResponseDto>> request(@AuthenticationPrincipal LoginUser user,
            @PathVariable String orderId, @RequestBody PaymentRefundRequestDto request) {
        var result = PaymentRefundResponseDto.from(refunds.request(user.id(), orderId, request.reason()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("전액 환불 신청을 확인했습니다.", result));
    }

    @GetMapping("/api/refund-requests/{requestId}")
    public ResponseEntity<ApiResponse<PaymentRefundResponseDto>> read(@AuthenticationPrincipal LoginUser user,
            @PathVariable String requestId) {
        var result = PaymentRefundResponseDto.from(refunds.read(user.id(), requestId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("전액 환불 처리 상태를 조회했습니다.", result));
    }
}
