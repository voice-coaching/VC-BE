package org.example.voice.analysis.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class SessionAnalysisNotFoundException extends BusinessException {
    public SessionAnalysisNotFoundException() {
        super(ErrorCode.SESSION_ANALYSIS_NOT_FOUND);
    }
}
