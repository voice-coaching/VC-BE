package org.example.voice.auth.exception;

import org.example.voice.common.exception.ErrorCode;

public class InvalidTokenException extends AuthException {
    public InvalidTokenException(ErrorCode errorCode) { super(errorCode); }
    public InvalidTokenException(ErrorCode errorCode, Throwable cause) { super(errorCode, cause); }
}
