package org.example.voice.payment.infrastructure;

import org.example.voice.payment.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class PortOneSecurityTest {
    private static final String SECRET = "dGVzdC13ZWJob29rLXNpZ25hdHVyZS1rZXk=";
    private TestPaymentSettings settings(boolean enabled, String users, String... profiles) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new TestPaymentSettings(enabled, users, "store", "channel", "test-only-secret", SECRET,
                environment);
    }
    @Test void productionAndMissingProfileOrAllowlistFailClosed() {
        assertThatThrownBy(() -> settings(true, "1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> settings(true, "1", "prod", "payment-test")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> settings(true, "", "payment-test")).isInstanceOf(IllegalStateException.class);
        assertThat(settings(false, "1").eligible(1L)).isFalse();
        assertThat(settings(true, "1", "payment-test").eligible(2L)).isFalse();
    }
    @Test void liveOrDifferentStoreAndChannelAreRejected() {
        var settings = settings(true, "1", "payment-test");
        for (var fields : new String[][]{{"store", "channel", "LIVE"}, {"wrong", "channel", "TEST"}, {"store", "wrong", "TEST"}}) {
            assertThatThrownBy(() -> settings.validate(new PaymentSnapshot("id", fields[0], fields[1], fields[2], "PAID", 15000, 0, "KRW", null, null)))
                    .isInstanceOf(PaymentException.class);
        }
    }
    @Test void officialSdkAcceptsSignedRawBodyAndRejectsTamperingAndReplayAge() throws Exception {
        var auth = new PortOneWebhookAuthentication(settings(true, "1", "payment-test"));
        var now = Long.toString(Instant.now().getEpochSecond());
        var body = "{\"type\":\"Transaction.Paid\",\"timestamp\":\"2026-10-28T00:00:00Z\",\"data\":{\"storeId\":\"store\",\"paymentId\":\"test-payment\",\"transactionId\":\"transaction\"}}";
        assertThat(auth.verify(body, "event-1", sign("event-1", now, body), now)).isEqualTo("test-payment");
        assertThatThrownBy(() -> auth.verify(body + " ", "event-1", sign("event-1", now, body), now)).isInstanceOf(PaymentException.class);
        var old = Long.toString(Instant.now().minusSeconds(600).getEpochSecond());
        assertThatThrownBy(() -> auth.verify(body, "event-1", sign("event-1", old, body), old)).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> auth.verify(body, "event-1", null, now)).isInstanceOf(PaymentException.class);
    }
    private String sign(String id, String timestamp, String body) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256"));
        return "v1," + Base64.getEncoder().encodeToString(mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }
}
