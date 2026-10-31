package org.example.voice.payment;

import org.example.voice.payment.application.*;
import org.example.voice.payment.domain.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRecoveryServiceTest {
    final PaymentTransactions transactions = mock(PaymentTransactions.class);
    final PaymentService payments = mock(PaymentService.class);
    final PaymentPolicy policy = mock(PaymentPolicy.class);
    final PaymentRecoveryService recovery = new PaymentRecoveryService(transactions, payments, policy);

    @Test void failedAndUnpaidOrdersDoNotStarveLaterPagesAndAreRetriedAfterWrap() {
        var a = new PaymentOrderStore.RecoveryCandidate("a", 1L);
        var b = new PaymentOrderStore.RecoveryCandidate("b", 2L);
        var c = new PaymentOrderStore.RecoveryCandidate("c", 3L);
        when(policy.eligible(anyLong())).thenReturn(true);
        when(transactions.recoveryCandidates("", 10)).thenReturn(List.of(a, b));
        when(transactions.recoveryCandidates("b", 10)).thenReturn(List.of(c));
        when(transactions.recoveryCandidates("c", 10)).thenReturn(List.of());
        when(payments.verify(1L, "a")).thenThrow(new PaymentException(502, "PAYMENT_PROVIDER_UNAVAILABLE"));
        when(payments.verify(2L, "b")).thenThrow(new PaymentException(409, "PAYMENT_NOT_COMPLETED"));

        assertThat(recovery.recover()).isEqualTo(new PaymentRecoveryService.Result(0, 1, 1, 0));
        assertThat(recovery.recover()).isEqualTo(new PaymentRecoveryService.Result(1, 0, 0, 0));
        assertThat(recovery.recover()).isEqualTo(new PaymentRecoveryService.Result(0, 1, 1, 0));
        verify(payments, times(2)).verify(1L, "a");
        verify(payments, times(2)).verify(2L, "b");
        verify(payments).verify(3L, "c");
    }

    @Test void ineligibleUsersNeverReachProviderAndUnexpectedFailuresStayIsolated() {
        when(transactions.recoveryCandidates("", 10)).thenReturn(List.of(
                new PaymentOrderStore.RecoveryCandidate("a", 1L),
                new PaymentOrderStore.RecoveryCandidate("b", 2L),
                new PaymentOrderStore.RecoveryCandidate("c", 3L)));
        when(policy.eligible(2L)).thenReturn(true);
        when(policy.eligible(3L)).thenReturn(true);
        when(payments.verify(2L, "b")).thenThrow(new IllegalStateException("private provider details"));
        assertThat(recovery.recover()).isEqualTo(new PaymentRecoveryService.Result(1, 0, 1, 1));
        verify(payments, never()).verify(1L, "a");
        verify(payments).verify(3L, "c");
    }

    @Test void failedCandidateQueryKeepsCursorAndDoesNotCallProvider() {
        when(transactions.recoveryCandidates("", 10)).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(recovery::recover).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(payments);
    }
}
