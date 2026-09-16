package org.example.voice.title.controller;

import org.example.voice.common.response.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(-31) @RestControllerAdvice(assignableTypes = TitleController.class)
public class TitleValidationHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> invalid(Exception ignored) {
        return ResponseEntity.badRequest().body(ApiResponse.error("입력값을 확인해 주세요.","VALIDATION_ERROR"));
    }
}
