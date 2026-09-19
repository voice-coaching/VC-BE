package org.example.voice.profileimage.controller;

import org.example.voice.common.response.ApiResponse;
import org.example.voice.profileimage.domain.ProfileImageException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Order(-20)
@RestControllerAdvice(assignableTypes = ProfileImageController.class)
public class ProfileImageExceptionHandler {
    @ExceptionHandler(ProfileImageException.class)
    ResponseEntity<ApiResponse<Void>> domain(ProfileImageException error) {
        return ResponseEntity.status(error.status()).body(ApiResponse.error("프로필 사진 요청을 처리할 수 없습니다.", error.code()));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> tooLarge(Exception ignored) {
        return ResponseEntity.status(413).body(ApiResponse.error("사진은 최대 5MB까지 등록할 수 있습니다.", "PAYLOAD_TOO_LARGE"));
    }
    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiResponse<Void>> missing(Exception ignored) {
        return ResponseEntity.badRequest().body(ApiResponse.error("사진 파일이 필요합니다.", "VALIDATION_ERROR"));
    }
}
