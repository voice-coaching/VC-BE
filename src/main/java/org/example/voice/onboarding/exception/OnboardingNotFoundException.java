package org.example.voice.onboarding.exception;

import org.example.voice.common.exception.BusinessException;
import org.example.voice.common.exception.ErrorCode;

public class OnboardingNotFoundException extends BusinessException {

    public OnboardingNotFoundException() {
        super(ErrorCode.ONBOARDING_NOT_FOUND);
    }
}
