package org.example.voice.training.exception;

import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;

/** Keeps schema opt-in error codes explicit without changing legacy business-error envelopes. */
public class AnalysisSchemaAdmissionException extends BaseException {
    public AnalysisSchemaAdmissionException(ErrorCode errorCode) {
        super(errorCode);
    }
}
