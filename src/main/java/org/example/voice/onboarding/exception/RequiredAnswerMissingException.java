package org.example.voice.onboarding.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class RequiredAnswerMissingException extends BusinessException {

    public RequiredAnswerMissingException() {
        super(ErrorCode.REQUIRED_ANSWER_MISSING);
    }
}
