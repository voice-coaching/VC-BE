package org.example.voice.training.controller;

import org.example.voice.common.response.ApiResponse;
import org.example.voice.training.exception.AnalysisSchemaAdmissionException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = TrainingAnalysisController.class)
public class AnalysisSchemaAdmissionExceptionHandler {
    @ExceptionHandler(AnalysisSchemaAdmissionException.class)
    public ResponseEntity<ApiResponse<Void>> admission(AnalysisSchemaAdmissionException exception) {
        var code = exception.getErrorCode();
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getMessage(), code.name()));
    }
}
