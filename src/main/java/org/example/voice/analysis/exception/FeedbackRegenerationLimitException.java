package org.example.voice.analysis.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class FeedbackRegenerationLimitException extends BusinessException {
    public FeedbackRegenerationLimitException() {
        super(ErrorCode.FEEDBACK_REGENERATION_LIMIT);
    }
}
