package org.example.voice.auth.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class AuthException extends BusinessException {
    public AuthException(ErrorCode errorCode) { super(errorCode); }
    public AuthException(ErrorCode errorCode, Throwable cause) { super(errorCode, errorCode.getMessage(), cause); }
}
