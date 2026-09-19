package org.example.voice.notification.controller;

import org.example.voice.common.response.ApiResponse;
import org.example.voice.notification.domain.NotificationException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(-40) @RestControllerAdvice(assignableTypes = NotificationController.class)
public class NotificationExceptionHandler {
    @ExceptionHandler(NotificationException.class)
    public ResponseEntity<ApiResponse<Void>> business(NotificationException error) {
        return ResponseEntity.status(error.status()).body(ApiResponse.error("알림 요청을 처리할 수 없습니다.", error.code()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.error("입력값을 확인해 주세요.", "VALIDATION_ERROR"));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> conflict() {
        return ResponseEntity.status(409).body(ApiResponse.error("등록 상태가 변경되었습니다. 다시 확인해 주세요.", "CONFLICT"));
    }
}
