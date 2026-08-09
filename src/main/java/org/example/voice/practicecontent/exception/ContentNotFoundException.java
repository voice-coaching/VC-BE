package org.example.voice.practicecontent.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class ContentNotFoundException extends BusinessException {

    public ContentNotFoundException() {
        super(ErrorCode.CONTENT_NOT_FOUND);
    }
}
