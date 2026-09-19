package org.example.voice.support.controller;

import org.example.voice.common.response.ApiResponse;
import org.example.voice.support.domain.SupportException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(-10)
@RestControllerAdvice(assignableTypes = SupportController.class)
public class SupportExceptionHandler {
    @ExceptionHandler(SupportException.class)
    public ResponseEntity<ApiResponse<Void>> business(SupportException error) {
        var code = error.getErrorCode();
        String wireCode = code.name().equals("ACCESS_DENIED") ? "FORBIDDEN" : code.name();
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.error(code.getMessage(), wireCode));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> validation(Exception ignored) {
        return ResponseEntity.badRequest().body(ApiResponse.error("입력값을 확인해 주세요.", "VALIDATION_ERROR"));
    }
}
