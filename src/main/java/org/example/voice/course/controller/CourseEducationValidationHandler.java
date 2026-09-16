package org.example.voice.course.controller;

import org.example.voice.common.response.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(-40) @RestControllerAdvice(assignableTypes = CourseEducationController.class)
public class CourseEducationValidationHandler {
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.error("요청 형식이 올바르지 않습니다.", "VALIDATION_ERROR"));
    }
}
