package org.example.voice.user.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class WithdrawalAlreadyProcessedException extends BusinessException {
    public WithdrawalAlreadyProcessedException() {
        super(ErrorCode.WITHDRAWAL_ALREADY_PROCESSED);
    }
}
