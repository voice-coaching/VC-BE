package org.example.voice.practicecontent.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class NextContentNotFoundException extends BusinessException {

    public NextContentNotFoundException() {
        super(ErrorCode.NEXT_CONTENT_NOT_FOUND);
    }
}
