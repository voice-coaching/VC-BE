package org.example.voice.analysis.controller;

import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.example.voice.common.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = DirectHistoryController.class)
public class DirectHistoryExceptionHandler {
    @ExceptionHandler(RunPodContractException.class)
    public ResponseEntity<ApiResponse<Void>> protocol(RunPodContractException error) {
        return ResponseEntity.status(error.status()).header("Cache-Control", "no-store")
                .body(ApiResponse.error("분석 이력 요청을 처리하지 못했습니다.", error.reason()));
    }
}
