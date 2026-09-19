package org.example.voice.practicecontent.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class ReferenceAudioNotFoundException extends BusinessException {

    public ReferenceAudioNotFoundException() {
        super(ErrorCode.REFERENCE_AUDIO_NOT_FOUND);
    }
}
