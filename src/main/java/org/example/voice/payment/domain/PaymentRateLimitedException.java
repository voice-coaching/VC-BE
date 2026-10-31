package org.example.voice.payment.domain;

public class PaymentRateLimitedException extends PaymentException {
    private final long retryAfterSeconds;
    public PaymentRateLimitedException(long retryAfterSeconds) {
        super(429, "PAYMENT_RATE_LIMITED");
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
