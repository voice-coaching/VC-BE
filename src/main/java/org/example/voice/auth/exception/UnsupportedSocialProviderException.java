package org.example.voice.auth.exception;

import org.example.voice.common.exception.ErrorCode;

public class UnsupportedSocialProviderException extends AuthException {
    public UnsupportedSocialProviderException() { super(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER); }
}
