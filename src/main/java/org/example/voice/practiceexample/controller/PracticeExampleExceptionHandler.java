package org.example.voice.practiceexample.controller;

import org.example.voice.common.response.ApiResponse;
import org.example.voice.practiceexample.domain.PracticeExampleException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(-45) @RestControllerAdvice
public class PracticeExampleExceptionHandler {
    @ExceptionHandler(PracticeExampleException.class)
    public ResponseEntity<ApiResponse<Void>> business(PracticeExampleException e) {
        return ResponseEntity.status(e.status()).contentType(org.springframework.http.MediaType.APPLICATION_JSON).header("Cache-Control", "no-store")
                .body(ApiResponse.error("예문 요청을 처리할 수 없습니다.", e.code()));
    }
}
