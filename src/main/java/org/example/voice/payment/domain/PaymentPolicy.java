package org.example.voice.payment.domain;

public interface PaymentPolicy {
    boolean eligible(Long userId);
    CheckoutSettings checkout();
    void validate(PaymentSnapshot payment);
    default void require(Long userId) {}
    record CheckoutSettings(String storeId, String channelKey) {}
}
