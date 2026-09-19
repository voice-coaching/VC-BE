package org.example.voice.analysis.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class AnalysisNotCompletedException extends BusinessException {
    public AnalysisNotCompletedException() {
        super(ErrorCode.ANALYSIS_NOT_COMPLETED);
    }
}
