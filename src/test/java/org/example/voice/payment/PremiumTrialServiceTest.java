package org.example.voice.payment;

import java.time.*;
import java.util.Optional;
import org.example.voice.common.exception.*;
import org.example.voice.payment.application.PremiumTrialService;
import org.example.voice.payment.domain.*;
import org.example.voice.payment.domain.port.PremiumTrialStore;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.port.UserReader;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PremiumTrialServiceTest {
    @Test void anExistingPaidMemberCannotConsumeTheSignupBenefit() {
        var store = mock(PremiumTrialStore.class);
        var memberships = mock(MembershipReader.class);
        var users = mock(UserReader.class);
        var clock = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC);
        var user = User.createSocial(null, "paid fixture", OffsetDateTime.now(clock));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(memberships.read(1L)).thenReturn(Membership.testPaid(OffsetDateTime.now(clock).plusMonths(1)));
        var service = new PremiumTrialService(store, memberships, users, clock);
        assertThat(service.status(1L).eligible()).isFalse();
        assertThatThrownBy(() -> service.activate(1L)).isInstanceOfSatisfying(BaseException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PREMIUM_TRIAL_NOT_ELIGIBLE));
        verify(store, never()).insert(any());
    }
}
