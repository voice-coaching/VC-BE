package org.example.voice.support.domain;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class SupportException extends BusinessException {
    public SupportException(ErrorCode code) { super(code); }
}
