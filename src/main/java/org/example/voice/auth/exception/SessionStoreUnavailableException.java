package org.example.voice.auth.exception;

import org.example.voice.common.exception.ErrorCode;

public class SessionStoreUnavailableException extends AuthException {
    public SessionStoreUnavailableException() { super(ErrorCode.AUTH_SESSION_STORE_UNAVAILABLE); }
}
