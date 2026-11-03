package org.example.voice.admin.controller;

import org.example.voice.common.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses=AdminController.class) @Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminExceptionHandler {
    @ExceptionHandler({OptimisticLockingFailureException.class,jakarta.persistence.OptimisticLockException.class,
            PessimisticLockingFailureException.class,jakarta.persistence.PessimisticLockException.class,jakarta.persistence.LockTimeoutException.class})
    public ResponseEntity<ApiResponse<Void>> conflict(Exception ignored) { return error(409,"ADMIN_EDIT_CONFLICT","대상 상태가 변경되었습니다. 다시 조회해 주세요."); }
    @ExceptionHandler({DataAccessException.class,jakarta.persistence.PersistenceException.class,org.springframework.transaction.CannotCreateTransactionException.class})
    public ResponseEntity<ApiResponse<Void>> unavailable(Exception ignored) { return error(503,"ADMIN_STORE_UNAVAILABLE","관리자 요청을 안전하게 저장하거나 조회할 수 없습니다."); }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(Exception ignored) { return error(400,"VALIDATION_ERROR","검색 조건을 확인해 주세요."); }
    private ResponseEntity<ApiResponse<Void>> error(int status,String code,String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(ApiResponse.error(message,code));
    }
}
