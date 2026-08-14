package org.example.voice.user.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class NicknameAlreadyExistsException extends BusinessException {
    public NicknameAlreadyExistsException() {
        super(ErrorCode.NICKNAME_ALREADY_EXISTS);
    }
}
