package org.example.voice.payment.domain;

public enum PaymentOperation {
    CREATE(5), VERIFY(10), READ(60), CANCEL(5);

    private final int limit;
    PaymentOperation(int limit) { this.limit = limit; }
    public int limit() { return limit; }
}
