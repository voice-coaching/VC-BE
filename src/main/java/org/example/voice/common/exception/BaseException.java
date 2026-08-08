package org.example.voice.common.exception;

public class BaseException extends BusinessException {

    public BaseException(String message) {
        super(ErrorCode.INVALID_INPUT_VALUE, message);
    }

    public BaseException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BaseException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }
}
