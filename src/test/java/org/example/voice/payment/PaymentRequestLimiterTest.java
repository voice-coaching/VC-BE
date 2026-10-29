package org.example.voice.payment;

import org.example.voice.payment.application.PaymentQuotaTransactions;
import org.example.voice.payment.application.PaymentRequestLimiter;
import org.example.voice.payment.domain.*;
import org.example.voice.payment.domain.entity.PaymentQuota;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRequestLimiterTest {
    @Test void fixedWindowRoundsRetryUpDoesNotExtendOnDenialAndResetsAtExactBoundary() {
        Instant start = Instant.parse("2026-10-29T00:00:00Z");
        for (var operation : PaymentOperation.values()) {
            var quota = new PaymentQuota(1L, operation, start);
            for (int i = 0; i < operation.limit(); i++) assertThat(quota.consume(start)).isZero();
            assertThat(quota.consume(start)).isEqualTo(60);
            assertThat(quota.consume(start.plusMillis(59500))).isEqualTo(1);
            assertThat(quota.consume(start.plusSeconds(60))).isZero();
            for (int i = 1; i < operation.limit(); i++) assertThat(quota.consume(start.plusSeconds(60))).isZero();
            assertThat(quota.consume(start.plusSeconds(60))).isEqualTo(60);
            // Backward wall clock changes must not create a fresh allowance.
            assertThat(quota.consume(start)).isEqualTo(120);
        }
    }

    @Test void disabledFeatureDoesNotTouchQuotaAndStorageFailureFailsClosed() {
        var transactions = mock(PaymentQuotaTransactions.class);
        var policy = mock(PaymentPolicy.class);
        var limiter = new PaymentRequestLimiter(transactions, policy);
        doThrow(new PaymentException(403, "TEST_PAYMENT_DISABLED")).when(policy).require(1L);
        assertThatThrownBy(() -> limiter.require(1L, PaymentOperation.CREATE))
                .isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.status()).isEqualTo(403));
        verifyNoInteractions(transactions);
        when(transactions.consume(2L, PaymentOperation.CREATE)).thenThrow(new IllegalStateException("private storage detail"));
        assertThatThrownBy(() -> limiter.require(2L, PaymentOperation.CREATE))
                .isInstanceOfSatisfying(PaymentException.class, e -> {
                    assertThat(e.status()).isEqualTo(503);
                    assertThat(e.code()).isEqualTo("PAYMENT_RATE_LIMIT_UNAVAILABLE");
                    assertThat(e.getMessage()).doesNotContain("private storage detail");
                });
        doReturn(13L).when(transactions).consume(2L, PaymentOperation.CREATE);
        assertThatThrownBy(() -> limiter.require(2L, PaymentOperation.CREATE))
                .isInstanceOfSatisfying(PaymentRateLimitedException.class, e -> assertThat(e.retryAfterSeconds()).isEqualTo(13));
    }
}
