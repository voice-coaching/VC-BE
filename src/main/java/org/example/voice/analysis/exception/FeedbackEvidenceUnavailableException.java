package org.example.voice.analysis.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class FeedbackEvidenceUnavailableException extends BusinessException {
    public FeedbackEvidenceUnavailableException() {
        super(ErrorCode.FEEDBACK_EVIDENCE_UNAVAILABLE);
    }
}
