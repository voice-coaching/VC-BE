package org.example.voice.analysis.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class AnalysisNotFoundException extends BusinessException {
    public AnalysisNotFoundException() {
        super(ErrorCode.ANALYSIS_NOT_FOUND);
    }
}
