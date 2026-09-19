package org.example.voice.title.controller;

import org.example.voice.common.response.ApiResponse;
import org.example.voice.title.domain.TitleException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(-30) @RestControllerAdvice
public class TitleExceptionHandler {
    // Also handles title-exam validation invoked by the existing training controller.
    @ExceptionHandler(TitleException.class)
    public ResponseEntity<ApiResponse<Void>> title(TitleException error) {
        String message = "ANALYSIS_SCORE_UNAVAILABLE".equals(error.getMessage())
                ? "발음 코칭은 완료됐지만 점수를 확정할 수 없어 승급 채점을 보류했습니다."
                : "승급 시험 요청을 처리할 수 없습니다.";
        return ResponseEntity.status(error.status()).body(ApiResponse.error(message, error.getMessage()));
    }
}
