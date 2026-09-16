package org.example.voice.practicecontent.controller;

import org.example.voice.common.response.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;

@Order(-40) @RestControllerAdvice(assignableTypes=CustomContentController.class)
public class CustomContentValidationHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(){return ResponseEntity.badRequest().body(ApiResponse.error("요청 형식이 올바르지 않습니다.","VALIDATION_ERROR"));}
}
