package org.example.voice.payment;

import org.example.voice.payment.domain.*;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;

class PaymentOrderTest {
    private final OffsetDateTime now = OffsetDateTime.parse("2026-01-31T12:00:00Z");
    private final PaymentOrder order = PaymentOrder.create(1L, "test-idempotency-0001", now);
    @Test void generatedPaymentIdMeetsKcpLengthAndCharacterRestrictions() {
        assertThat(order.getId()).hasSize(36).matches("test[a-f0-9]{32}");
    }
    private PaymentSnapshot snapshot(String status, long total, long cancelled, OffsetDateTime updated) {
        return new PaymentSnapshot(order.getId(), "store", "channel", "TEST", status, total, cancelled, "KRW", now, updated);
    }
    @Test void verifiedPaidGrantsOneCalendarMonthAndRetriesNeverExtendIt() {
        order.apply(snapshot("PAID", 15000, 0, now), now);
        assertThat(order.getExpiresAt()).isEqualTo(OffsetDateTime.parse("2026-02-28T12:00:00Z"));
        order.apply(snapshot("PAID", 15000, 0, now.plusMinutes(1)), now.plusMinutes(1));
        assertThat(order.getExpiresAt()).isEqualTo(OffsetDateTime.parse("2026-02-28T12:00:00Z"));
    }
    @Test void cancelledPaymentCannotBeResurrectedEvenByNewerPaidSnapshot() {
        order.apply(snapshot("PAID", 15000, 0, now), now);
        order.apply(snapshot("CANCELLED", 15000, 15000, now.plusSeconds(1)), now.plusSeconds(1));
        order.apply(snapshot("PAID", 15000, 0, now.plusSeconds(2)), now.plusSeconds(2));
        assertThat(order.getStatus()).isEqualTo("CANCELLED");
    }
    @Test void partialRefundFailsClosedAndStaleSnapshotsCannotOverwriteState() {
        order.apply(snapshot("PAID", 15000, 0, now.plusSeconds(2)), now.plusSeconds(2));
        order.apply(snapshot("FAILED", 15000, 0, now), now.plusSeconds(3));
        assertThat(order.getStatus()).isEqualTo("PAID");
        order.apply(snapshot("PARTIAL_CANCELLED", 15000, 1000, now.plusSeconds(4)), now.plusSeconds(4));
        assertThat(order.getStatus()).isEqualTo("CANCELLED");
    }
    @Test void cancellationWinsEvenWhenProviderTimestampsAreEqualOrOlder() {
        for (int offset : new int[]{0, -1}) {
            var candidate = PaymentOrder.create(1L, "cancellation-time-" + offset, now);
            candidate.apply(new PaymentSnapshot(candidate.getId(), "store", "channel", "TEST", "PAID", 15000, 0, "KRW", now, now), now);
            candidate.apply(new PaymentSnapshot(candidate.getId(), "store", "channel", "TEST", "CANCELLED", 15000, 15000, "KRW", now, now.plusSeconds(offset)), now);
            assertThat(candidate.getStatus()).isEqualTo("CANCELLED");
        }
    }
    @Test void alteredAmountCurrencyAndIdentityNeverGrantMembership() {
        assertThatThrownBy(() -> order.apply(snapshot("PAID", 1, 0, now), now)).isInstanceOf(PaymentException.class);
        var other = new PaymentSnapshot("another", "store", "channel", "TEST", "PAID", 15000, 0, "KRW", now, now);
        assertThatThrownBy(() -> order.apply(other, now)).isInstanceOf(PaymentException.class);
        var usd = new PaymentSnapshot(order.getId(), "store", "channel", "TEST", "PAID", 15000, 0, "USD", now, now);
        assertThatThrownBy(() -> order.apply(usd, now)).isInstanceOf(PaymentException.class);
        assertThat(order.getStatus()).isEqualTo("PENDING");
    }
    @Test void futureAndUnreasonablyOldPaymentTimestampsAreRejected() {
        var future = new PaymentSnapshot(order.getId(), "store", "channel", "TEST", "PAID", 15000, 0, "KRW", now.plusDays(1), now);
        assertThatThrownBy(() -> order.apply(future, now)).isInstanceOf(PaymentException.class);
        var old = new PaymentSnapshot(order.getId(), "store", "channel", "TEST", "PAID", 15000, 0, "KRW", now.minusDays(1), now);
        assertThatThrownBy(() -> order.apply(old, now)).isInstanceOf(PaymentException.class);
    }
}
