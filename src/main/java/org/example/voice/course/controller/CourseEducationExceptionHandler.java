package org.example.voice.course.controller;

import org.example.voice.common.response.ApiResponse;
import org.example.voice.course.domain.CourseEducationException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Order(-30) @RestControllerAdvice
public class CourseEducationExceptionHandler {
    @ExceptionHandler(CourseEducationException.class)
    public ResponseEntity<ApiResponse<Void>> handle(CourseEducationException error) {
        return ResponseEntity.status(error.status()).body(ApiResponse.error("클래스 교육 내용을 처리할 수 없습니다.", error.getMessage()));
    }
}
