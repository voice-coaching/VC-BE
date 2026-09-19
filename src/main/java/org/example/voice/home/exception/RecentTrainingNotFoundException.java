package org.example.voice.home.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class RecentTrainingNotFoundException extends BusinessException {

    public RecentTrainingNotFoundException() {
        super(ErrorCode.RECENT_TRAINING_NOT_FOUND);
    }
}
