package org.example.voice.payment.infrastructure;

import org.example.voice.payment.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class PortOneSecurityTest {
    private static final String SECRET = "dGVzdC13ZWJob29rLXNpZ25hdHVyZS1rZXk=";
    private TestPaymentSettings settings(boolean enabled) {
        return new TestPaymentSettings(enabled, "store", "channel", "test-only-secret", SECRET);
    }
    @Test void enabledTestPaymentsAcceptEveryPositiveUserWithoutProfileOrAllowlist() {
        var enabled = settings(true);
        assertThat(enabled.eligible(1L)).isTrue();
        assertThat(enabled.eligible(2L)).isTrue();
        assertThat(enabled.eligible(Long.MAX_VALUE)).isTrue();
        assertThat(enabled.eligible(null)).isFalse();
        assertThat(enabled.eligible(0L)).isFalse();
        assertThat(enabled.eligible(-1L)).isFalse();
        assertThat(settings(false).eligible(1L)).isFalse();
    }
    @Test void activationRequiresEveryCredentialButDisabledStartupDoesNot() {
        String[] credentials = {"store", "channel", "test-only-secret", SECRET};
        for (int i = 0; i < credentials.length; i++) {
            var missing = credentials.clone();
            missing[i] = " ";
            assertThatThrownBy(() -> new TestPaymentSettings(true, missing[0], missing[1], missing[2], missing[3]))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Test payments require store, TEST channel, API secret and webhook secret");
        }
        assertThat(new TestPaymentSettings(false, "", "", "", "").eligible(1L)).isFalse();
    }
    @Test void springBindingSupportsExistingProfilesAndIgnoresLegacyTesterList() {
        var runner = new ApplicationContextRunner().withUserConfiguration(TestPaymentSettings.class);
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(TestPaymentSettings.class).eligible(2L)).isFalse();
        });
        for (var profile : new String[]{"default", "prod", "production", "payment-test"}) {
            runner.withPropertyValues("spring.profiles.active=" + profile, "payment.test.enabled=true",
                    "payment.test.allowed-user-ids=obsolete-list", "PORTONE_STORE_ID=store",
                    "PORTONE_TEST_CHANNEL_KEY=channel", "PORTONE_API_SECRET=fixture-secret",
                    "PORTONE_WEBHOOK_SECRET=" + SECRET).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(TestPaymentSettings.class).eligible(2L)).isTrue();
            });
        }
    }
    @Test void liveOrDifferentStoreAndChannelAreRejected() {
        var settings = settings(true);
        for (var fields : new String[][]{{"store", "channel", "LIVE"}, {"wrong", "channel", "TEST"}, {"store", "wrong", "TEST"}}) {
            assertThatThrownBy(() -> settings.validate(new PaymentSnapshot("id", fields[0], fields[1], fields[2], "PAID", 15000, 0, "KRW", null, null)))
                    .isInstanceOf(PaymentException.class);
        }
    }
    @Test void officialSdkAcceptsSignedRawBodyAndRejectsTamperingAndReplayAge() throws Exception {
        var auth = new PortOneWebhookAuthentication(settings(true));
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
