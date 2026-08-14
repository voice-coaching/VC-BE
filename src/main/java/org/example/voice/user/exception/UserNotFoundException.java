package org.example.voice.user.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class UserNotFoundException extends BusinessException {
    public UserNotFoundException() {
        super(ErrorCode.USER_NOT_FOUND);
    }
}
