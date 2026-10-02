package org.example.voice.analysis.controller;

import org.example.voice.analysis.exception.CanonicalAnalysisViewException;
import org.example.voice.common.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** New route only. Never expose/log private parser, database or evidence diagnostics. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=CanonicalAnalysisController.class)
public class CanonicalAnalysisExceptionHandler {
    @ExceptionHandler(CanonicalAnalysisViewException.class)
    public ResponseEntity<ApiResponse<Void>> expected(CanonicalAnalysisViewException exception) { return reply(exception.reason()); }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(MethodArgumentTypeMismatchException ignored) {
        return reply(CanonicalAnalysisViewException.Reason.INVALID_ANALYSIS_ID);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unavailable(Exception ignored) {
        return reply(CanonicalAnalysisViewException.Reason.CANONICAL_RESULT_UNAVAILABLE);
    }
    private static ResponseEntity<ApiResponse<Void>> reply(CanonicalAnalysisViewException.Reason reason) {
        return ResponseEntity.status(reason.status).header("Cache-Control","no-store")
                .body(ApiResponse.error(reason.message,reason.name()));
    }
}
