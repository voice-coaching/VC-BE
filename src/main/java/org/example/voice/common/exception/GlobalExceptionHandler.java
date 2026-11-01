package org.example.voice.common.exception;

import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolationException;
import org.example.voice.common.response.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartSize(Exception ignored) {
        return ResponseEntity.status(413).body(ApiResponse.error("업로드 용량 제한을 초과했습니다.", "PAYLOAD_TOO_LARGE"));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        log.atInfo().addKeyValue("event","api_rejected").addKeyValue("error_code",errorCode.name()).log("API request rejected");
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(exception.getMessage(), errorCode.name()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        log.atInfo().addKeyValue("event","api_rejected").addKeyValue("error_code","VALIDATION_ERROR").log("Request validation failed");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        log.atInfo().addKeyValue("event","api_rejected").addKeyValue("error_code","INVALID_INPUT_VALUE").log("Request constraint violation");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(exception.getConstraintViolations().stream()
                        .allMatch(violation -> violation.getConstraintDescriptor().getAnnotation()
                                .annotationType().equals(jakarta.validation.constraints.Email.class))
                        && !exception.getConstraintViolations().isEmpty()
                        ? ErrorCode.INVALID_EMAIL_FORMAT.getMessage()
                        : ErrorCode.INVALID_INPUT_VALUE.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestParameter(MissingServletRequestParameterException exception) {
        log.atInfo().addKeyValue("event","api_rejected").addKeyValue("error_code","VALIDATION_ERROR").log("Required request parameter missing");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.getMessage(), ErrorCode.VALIDATION_ERROR.name()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        log.warn("Unsupported request Content-Type");
        ErrorCode errorCode = ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .headers(exception.getHeaders())
                .body(ApiResponse.error(errorCode.getMessage(), errorCode.name()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException exception) {
        log.warn("Request message was not readable");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("DB constraint violation");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        log.atError().addKeyValue("event","api_failed").addKeyValue("exception_type",exception.getClass().getName())
                .setCause(exception).log("Unexpected API error");
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
