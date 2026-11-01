package org.example.voice.coaching;

import org.example.voice.coaching.application.CoachingUsageService;
import org.example.voice.coaching.domain.*;
import org.example.voice.coaching.domain.port.*;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoachingUsageServiceTest {
    final CoachingUsageStore store = mock(CoachingUsageStore.class);
    final CoachingEntitlementReader entitlements = mock(CoachingEntitlementReader.class);
    final OffsetDateTime start = OffsetDateTime.parse("2026-01-31T00:00:00Z");
    final Clock clock = Clock.fixed(start.toInstant(), ZoneOffset.UTC);
    final CoachingUsageService service = new CoachingUsageService(store, entitlements, clock);
    final UUID request = UUID.randomUUID();

    void trial(long used, long reserved) {
        when(store.trialStartedAt(1L)).thenReturn(start);
        when(entitlements.find(1L, start)).thenReturn(Optional.empty());
        when(store.counts(1L, "free")).thenReturn(new CoachingUsageStore.Counts(used, reserved));
    }
    @Test void calendarMonthDoesNotBecomeThirtyDaysOrRenewAfterExpiry() {
        var window = CoachingWindow.trial(start);
        assertThat(window.expiresAt()).isEqualTo("2026-02-28T00:00:00Z");
        assertThat(window.active(start.minusNanos(1))).isFalse();
        assertThat(window.active(window.expiresAt().minusNanos(1))).isTrue();
        assertThat(window.active(window.expiresAt())).isFalse();
    }
    @Test void bothConsumedAndPendingRequestsUseTheLimit() {
        trial(6, 2);
        assertThat(service.getUsage(1L).remaining()).isZero();
        assertThat(service.getUsage(1L).canRequestCoaching()).isFalse();
        assertCode(() -> reserve("문장", 90000), ErrorCode.COACHING_QUOTA_EXCEEDED);
        verify(store, never()).reserve(any(), any(), any(), any(), any());
    }
    @Test void exactScriptAndAudioBoundsAreAcceptedAndUnicodeUsesCodePoints() {
        trial(7, 0);
        String text = "😀".repeat(300);
        reserve(text, 90000);
        verify(store).reserve(1L, 2L, 3L, request, CoachingWindow.trial(start));
        assertThat(service.getUsage(1L).remaining()).isEqualTo(1);
    }
    @ParameterizedTest @CsvSource({"301,90000,COACHING_SCRIPT_LIMIT_EXCEEDED", "300,90001,COACHING_AUDIO_LIMIT_EXCEEDED", "300,0,COACHING_AUDIO_LIMIT_EXCEEDED"})
    void invalidSourcesNeverReserve(int chars, int duration, ErrorCode code) {
        assertCode(() -> reserve("가".repeat(chars), duration), code);
        verifyNoInteractions(store, entitlements);
    }
    @Test void expiredTrialRejectsEvenWithUnusedCredits() {
        trial(0, 0);
        var later = new CoachingUsageService(store, entitlements,
                Clock.fixed(start.plusMonths(1).toInstant(), ZoneOffset.UTC));
        when(entitlements.find(1L, start.plusMonths(1))).thenReturn(Optional.empty());
        assertThat(later.getUsage(1L).remaining()).isZero();
        assertCode(() -> later.reserve(1L,2L,3L,request,"문장",1000), ErrorCode.COACHING_PERIOD_EXPIRED);
    }
    @Test void paidWindowUsesThirtyAndDoesNotSpendTrialCredits() {
        var paid = new CoachingWindow("paid:receipt", "paid", "TEST", start, start.plusMonths(1), 30);
        when(store.trialStartedAt(1L)).thenReturn(start.minusYears(1));
        when(entitlements.find(1L, start)).thenReturn(Optional.of(paid));
        when(store.counts(1L, paid.key())).thenReturn(new CoachingUsageStore.Counts(29,0));
        reserve("문장",1000);
        verify(store).reserve(1L,2L,3L,request,paid);
        assertThat(service.getUsage(1L).remaining()).isEqualTo(1);
    }
    @Test void adminExemptsCountButStillValidatesMediaBounds() {
        when(store.trialStartedAt(1L)).thenReturn(start);
        when(entitlements.find(1L,start)).thenReturn(Optional.of(CoachingWindow.admin()));
        when(store.counts(1L,"admin")).thenReturn(new CoachingUsageStore.Counts(0,0));
        reserve("문장",90000);
        assertThat(service.getUsage(1L).unlimited()).isTrue();
        assertThat(service.getUsage(1L).remaining()).isNull();
        verify(store, never()).reserve(any(),any(),any(),any(),any());
        assertCode(() -> reserve("문장",90001), ErrorCode.COACHING_AUDIO_LIMIT_EXCEEDED);
    }
    void reserve(String text, int ms) { service.reserve(1L,2L,3L,request,text,ms); }
    static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BaseException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
