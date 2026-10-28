package org.example.voice.payment.controller;

import org.example.voice.common.response.ApiResponse;
import org.example.voice.payment.domain.PaymentException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Order(-20)
@RestControllerAdvice(assignableTypes = {PaymentController.class, PaymentRefundController.class})
public class PaymentExceptionHandler {
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiResponse<Void>> handle(PaymentException error) {
        return ResponseEntity.status(error.status()).cacheControl(org.springframework.http.CacheControl.noStore())
                .body(ApiResponse.error("결제 요청을 처리할 수 없습니다.", error.code()));
    }
    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> missingHeader() {
        return ResponseEntity.badRequest().body(ApiResponse.error("Idempotency-Key 헤더가 필요합니다.", "INVALID_IDEMPOTENCY_KEY"));
    }
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> invalidQueryType() {
        return ResponseEntity.badRequest().body(ApiResponse.error("페이지 번호와 크기는 정수여야 합니다.", "VALIDATION_ERROR"));
    }
}
