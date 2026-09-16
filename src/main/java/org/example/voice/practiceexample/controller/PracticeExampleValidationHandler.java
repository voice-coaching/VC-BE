package org.example.voice.practiceexample.controller;

import org.example.voice.common.response.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(-46) @RestControllerAdvice(assignableTypes = PracticeExampleController.class)
public class PracticeExampleValidationHandler {
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.error("입력값을 확인해 주세요.", "VALIDATION_ERROR"));
    }
}
