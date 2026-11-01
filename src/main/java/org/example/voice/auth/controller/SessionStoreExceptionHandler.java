package org.example.voice.auth.controller;

import org.example.voice.auth.exception.SessionStoreUnavailableException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.common.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SessionStoreExceptionHandler {
    @ExceptionHandler(SessionStoreUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> unavailable(SessionStoreUnavailableException ignored) {
        var error=ErrorCode.AUTH_SESSION_STORE_UNAVAILABLE;
        return ResponseEntity.status(503).header("Cache-Control","no-store").header("Retry-After","1")
                .body(ApiResponse.error(error.getMessage(),error.name()));
    }
}
