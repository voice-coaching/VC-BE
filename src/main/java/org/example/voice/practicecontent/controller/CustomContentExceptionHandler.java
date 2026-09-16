package org.example.voice.practicecontent.controller;

import org.example.voice.common.response.ApiResponse;
import org.example.voice.practicecontent.domain.CustomContentException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Order(-30) @RestControllerAdvice
public class CustomContentExceptionHandler {
    @ExceptionHandler(CustomContentException.class)
    public ResponseEntity<ApiResponse<Void>> custom(CustomContentException e){return ResponseEntity.status(e.status()).body(ApiResponse.error("사용자 문장 요청을 처리할 수 없습니다.",e.getMessage()));}
}
