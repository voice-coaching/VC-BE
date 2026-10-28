package org.example.voice.payment.infrastructure;

import org.example.voice.payment.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TestPaymentSettings implements PaymentPolicy {
    private final boolean enabled;
    private final Set<Long> users;
    private final String storeId;
    private final String channelKey;
    private final String apiSecret;
    private final String webhookSecret;
    public TestPaymentSettings(
            @Value("${payment.test.enabled:false}") boolean enabled,
            @Value("${payment.test.allowed-user-ids:}") String userIds,
            @Value("${PORTONE_STORE_ID:}") String storeId,
            @Value("${PORTONE_TEST_CHANNEL_KEY:}") String channelKey,
            @Value("${PORTONE_API_SECRET:}") String apiSecret,
            @Value("${PORTONE_WEBHOOK_SECRET:}") String webhookSecret,
            Environment environment) {
        this.enabled = enabled;
        this.users = Arrays.stream(userIds.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).collect(Collectors.toUnmodifiableSet());
        this.storeId = storeId; this.channelKey = channelKey; this.apiSecret = apiSecret; this.webhookSecret = webhookSecret;
        var profiles = Set.of(environment.getActiveProfiles());
        if (enabled && (!profiles.contains("payment-test") || profiles.contains("prod") || profiles.contains("production")
                || users.isEmpty() || users.stream().anyMatch(id -> id < 1)
                || storeId.isBlank() || channelKey.isBlank() || apiSecret.isBlank()))
            throw new IllegalStateException("Test payments require the payment-test profile, explicit testers and credentials; production profiles are prohibited");
    }
    @Override public boolean eligible(Long userId) { return enabled && users.contains(userId); }
    @Override public CheckoutSettings checkout() { return new CheckoutSettings(storeId, channelKey); }
    @Override public void validate(PaymentSnapshot payment) {
        if (!storeId.equals(payment.storeId()) || !channelKey.equals(payment.channelKey()) || !"TEST".equals(payment.channelType()))
            throw new PaymentException(409, "PAYMENT_CHANNEL_MISMATCH");
    }
    boolean enabled() { return enabled; }
    String apiSecret() { return apiSecret; }
    String webhookSecret() { return webhookSecret; }
}
