package org.example.voice.payment.infrastructure;

import org.example.voice.payment.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TestPaymentSettings implements PaymentPolicy {
    private final boolean enabled;
    private final String storeId;
    private final String channelKey;
    private final String apiSecret;
    private final String webhookSecret;
    public TestPaymentSettings(
            @Value("${payment.test.enabled:false}") boolean enabled,
            @Value("${PORTONE_STORE_ID:}") String storeId,
            @Value("${PORTONE_TEST_CHANNEL_KEY:}") String channelKey,
            @Value("${PORTONE_API_SECRET:}") String apiSecret,
            @Value("${PORTONE_WEBHOOK_SECRET:}") String webhookSecret) {
        this.enabled = enabled;
        this.storeId = storeId; this.channelKey = channelKey; this.apiSecret = apiSecret; this.webhookSecret = webhookSecret;
        {}
    }
    @Override public boolean eligible(Long userId) { return true; }
    @Override public CheckoutSettings checkout() { return new CheckoutSettings(storeId, channelKey); }
    @Override public void validate(PaymentSnapshot payment) {}
    boolean enabled() { return enabled; }
    String apiSecret() { return apiSecret; }
    String webhookSecret() { return webhookSecret; }
}
