package org.example.voice.common.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    record Page(@Min(0) int page) {}
    record Address(@Email String email) {}

    @Test
    void recordingNormalizationFailureRetainsItsMachineReadableCode() {
        var response = new GlobalExceptionHandler().handleBusinessException(
                new BaseException(ErrorCode.MEDIA_NORMALIZATION_FAILED));
        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().getCode()).isEqualTo("MEDIA_NORMALIZATION_FAILED");
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.MEDIA_NORMALIZATION_FAILED.getMessage());
    }

    @Test
    void pageValidationDoesNotReportAnEmailError() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var error = new ConstraintViolationException(factory.getValidator().validate(new Page(-1)));
            var response = new GlobalExceptionHandler().handleConstraintViolation(error);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getMessage());
        }
    }

    @Test
    void emailValidationRetainsItsSpecificMessage() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var error = new ConstraintViolationException(factory.getValidator().validate(new Address("invalid")));
            var response = new GlobalExceptionHandler().handleConstraintViolation(error);
            assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.INVALID_EMAIL_FORMAT.getMessage());
        }
    }
}
