package org.example.voice.payment;

import org.example.voice.payment.application.TestMembershipService;
import org.example.voice.payment.domain.PaymentOrderStore;
import org.example.voice.payment.domain.PaymentPolicy;
import org.example.voice.payment.domain.port.TestMembershipGrantReader;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.port.UserReader;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TestMembershipServiceTest {
    private final PaymentPolicy policy = mock(PaymentPolicy.class);
    private final PaymentOrderStore orders = mock(PaymentOrderStore.class);
    private final UserReader users = mock(UserReader.class);
    private final TestMembershipGrantReader grants = mock(TestMembershipGrantReader.class);
    private final Clock clock = Clock.fixed(OffsetDateTime.parse("2026-10-28T00:00:00Z").toInstant(), ZoneOffset.UTC);
    private final TestMembershipService service = new TestMembershipService(policy, orders, users, clock, grants);

    @Test void existingGrantIsAdminUsageTierWithoutEnablingPaymentsOrChangingRole() {
        var user = User.createLocal("fixture@example.invalid", "hash", "fixture", OffsetDateTime.now(clock));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(grants.exists(1L)).thenReturn(true);
        var membership = service.read(1L);
        assertThat(membership.type()).isEqualTo("admin");
        assertThat(membership.expiresAt()).isNull();
        assertThat(membership.environment()).isEqualTo("TEST");
        assertThat(user.getRole().name()).isEqualTo("USER");
        verifyNoInteractions(policy, orders);
    }

    @Test void newMemberIsFreeAndUsesExistingPaymentRulesWhenEligible() {
        when(users.findById(2L)).thenReturn(Optional.of(User.createSocial(null, "new", OffsetDateTime.now(clock))));
        assertThat(service.read(2L).type()).isEqualTo("free");
        verifyNoInteractions(orders);
        when(policy.eligible(2L)).thenReturn(true);
        var expires = OffsetDateTime.now(clock).plusMonths(1);
        when(orders.activeUntil(2L, OffsetDateTime.now(clock))).thenReturn(Optional.of(expires));
        assertThat(service.read(2L).type()).isEqualTo("paid");
        assertThat(service.read(2L).expiresAt()).isEqualTo(expires);
        when(orders.activeUntil(2L, OffsetDateTime.now(clock))).thenReturn(Optional.empty());
        assertThat(service.read(2L).type()).isEqualTo("free");
    }

    @Test void missingWithdrawnAndSuspendedMembersNeverReceiveAdminOrPaid() {
        when(users.findById(1L)).thenReturn(Optional.empty());
        assertThat(service.read(1L).type()).isEqualTo("free");
        var withdrawn = User.createLocal("fixture@example.invalid", "hash", "fixture", OffsetDateTime.now(clock));
        withdrawn.withdraw(OffsetDateTime.now(clock));
        when(users.findById(2L)).thenReturn(Optional.of(withdrawn));
        assertThat(service.read(2L).type()).isEqualTo("free");
        var suspended = mock(User.class);
        when(suspended.isSuspended()).thenReturn(true);
        when(users.findById(3L)).thenReturn(Optional.of(suspended));
        assertThat(service.read(3L).type()).isEqualTo("free");
        verifyNoInteractions(policy, grants, orders);
    }
}
