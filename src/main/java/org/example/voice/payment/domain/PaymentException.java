package org.example.voice.payment.domain;

public class PaymentException extends RuntimeException {
    private final int status;
    private final String code;
    public PaymentException(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
}
