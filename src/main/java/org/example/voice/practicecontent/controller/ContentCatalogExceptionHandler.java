package org.example.voice.practicecontent.controller;

import org.example.voice.common.response.ApiResponse;
import org.example.voice.practicecontent.domain.ContentCatalogException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Order(-30) @RestControllerAdvice
public class ContentCatalogExceptionHandler {
    @ExceptionHandler(ContentCatalogException.class)
    public ResponseEntity<ApiResponse<Void>> handle(ContentCatalogException error){return ResponseEntity.status(error.status()).body(ApiResponse.error("콘텐츠 탐색 요청을 처리할 수 없습니다.",error.getMessage()));}
}
