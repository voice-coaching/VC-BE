package org.example.voice.analysis.controller;

import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.example.voice.common.exception.BusinessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** New evidence-only error contract; legacy claim/heartbeat/result errors stay unchanged. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes={CanonicalEvidenceController.class,CanonicalJournalController.class,CanonicalHandoffController.class,CanonicalHandoffReadinessController.class,CanonicalArchiveReconciliationController.class})
public class CanonicalEvidenceExceptionHandler {
    @ExceptionHandler(RunPodContractException.class)
    public ResponseEntity<Failure> protocol(RunPodContractException error){return response(error.status(),error.reason());}
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Failure> business(BusinessException error){
        return response(error.getErrorCode().getHttpStatus().value()==401?401:422,
                error.getErrorCode().getHttpStatus().value()==401?"UNAUTHENTICATED":"VALIDATION_FAILED");
    }
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Failure> invalid(Exception error){return response(422,"VALIDATION_FAILED");}
    @ExceptionHandler({DataAccessException.class,org.springframework.transaction.TransactionException.class})
    public ResponseEntity<Failure> dependency(Exception error){return response(503,"DEPENDENCY_UNAVAILABLE");}
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Failure> unexpected(Exception error){return response(500,"INTERNAL_ERROR");}
    private ResponseEntity<Failure> response(int status,String reason){
        boolean retryable=status==429 || status==502 || status==503 || status==504;
        var builder=ResponseEntity.status(status).header("Cache-Control","no-store");
        if(retryable)builder.header("Retry-After","1");
        if(status==401)builder.header("WWW-Authenticate","Bearer");
        return builder.body(new Failure(RunPodContract.CAPABILITY_VERSION,reason,retryable));
    }
    public record Failure(String contractVersion,String reasonCode,boolean retryable){}
}
