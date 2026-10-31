package org.example.voice.payment.domain;

public interface PaymentPolicy {
    boolean eligible(Long userId);
    CheckoutSettings checkout();
    void validate(PaymentSnapshot payment);
    default void require(Long userId) {
        if (!eligible(userId)) throw new PaymentException(403, "TEST_PAYMENT_DISABLED");
    }
    record CheckoutSettings(String storeId, String channelKey) {}
}
