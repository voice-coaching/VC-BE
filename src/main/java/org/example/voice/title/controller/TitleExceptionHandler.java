package org.example.voice.title.controller;

import org.example.voice.common.response.ApiResponse;
import org.example.voice.title.domain.TitleException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(-30) @RestControllerAdvice
public class TitleExceptionHandler {
    // Also handles title-exam validation invoked by the existing training controller.
    @ExceptionHandler(TitleException.class)
    public ResponseEntity<ApiResponse<Void>> title(TitleException error) {
        return ResponseEntity.status(error.status()).body(ApiResponse.error("승급 시험 요청을 처리할 수 없습니다.",error.getMessage()));
    }
}
