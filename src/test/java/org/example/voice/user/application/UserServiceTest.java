package org.example.voice.user.application;

import org.example.voice.onboarding.domain.entity.OnboardingProfile;
import org.example.voice.onboarding.domain.port.OnboardingProfileReader;
import org.example.voice.consent.domain.port.ProcessingConsentLedger;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.port.LoginProviderReader;
import org.example.voice.user.domain.port.UserReader;
import org.example.voice.user.domain.port.UserSessionRevoker;
import org.example.voice.user.domain.port.UserWriter;
import org.example.voice.user.domain.type.UserStatus;
import org.example.voice.user.exception.NicknameAlreadyExistsException;
import org.example.voice.user.exception.WithdrawalAlreadyProcessedException;
import org.example.voice.training.domain.port.RecordingDeletionScheduler;
import org.example.voice.training.domain.type.RecordingDeletionReason;
import org.example.voice.training.domain.port.RecordingUploadIntentRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserReader userReader;
    private UserWriter userWriter;
    private LoginProviderReader loginProviderReader;
    private UserSessionRevoker userSessionRevoker;
    private OnboardingProfileReader onboardingProfileReader;
    private ProcessingConsentLedger processingConsentLedger;
    private RecordingDeletionScheduler recordingDeletionScheduler;
    private RecordingUploadIntentRegistry uploadIntentRegistry;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userReader = mock(UserReader.class);
        userWriter = mock(UserWriter.class);
        loginProviderReader = mock(LoginProviderReader.class);
        userSessionRevoker = mock(UserSessionRevoker.class);
        onboardingProfileReader = mock(OnboardingProfileReader.class);
        processingConsentLedger = mock(ProcessingConsentLedger.class);
        recordingDeletionScheduler = mock(RecordingDeletionScheduler.class);
        uploadIntentRegistry = mock(RecordingUploadIntentRegistry.class);
        userService = new UserService(
                userReader,
                userWriter,
                loginProviderReader,
                userSessionRevoker,
                onboardingProfileReader,
                processingConsentLedger,
                recordingDeletionScheduler,
                uploadIntentRegistry
        );
    }

    @Test
    void getMyProfileCombinesLocalAndSocialLoginProviders() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-14T00:00:00Z");
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getNickname()).thenReturn("또박이");
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getCreatedAt()).thenReturn(createdAt);
        when(user.hasLocalCredential()).thenReturn(true);
        when(userReader.findById(1L)).thenReturn(Optional.of(user));
        when(loginProviderReader.findByUserId(1L)).thenReturn(List.of("GOOGLE", "KAKAO", "GOOGLE"));
        when(onboardingProfileReader.findByUserId(1L)).thenReturn(Optional.of(mock(OnboardingProfile.class)));

        var result = userService.getMyProfile(1L);

        assertThat(result.loginProviders()).containsExactly("LOCAL", "GOOGLE", "KAKAO");
        assertThat(result.onboardingCompleted()).isTrue();
        assertThat(result.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void updateMyProfileTrimsAndSavesNickname() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getNickname()).thenReturn("새닉네임");
        when(user.getUpdatedAt()).thenReturn(OffsetDateTime.parse("2026-08-14T00:00:00Z"));
        when(userReader.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        var result = userService.updateMyProfile(1L, "  새닉네임  ");

        verify(userReader).existsByNicknameExcludingUserId("새닉네임", 1L);
        verify(user).updateNickname(eq("새닉네임"), any(OffsetDateTime.class));
        verify(userWriter).save(user);
        assertThat(result.nickname()).isEqualTo("새닉네임");
    }

    @Test
    void updateMyProfileRejectsDuplicateNickname() {
        User user = mock(User.class);
        when(userReader.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userReader.existsByNicknameExcludingUserId("중복", 1L)).thenReturn(true);

        assertThatThrownBy(() -> userService.updateMyProfile(1L, "중복"))
                .isInstanceOf(NicknameAlreadyExistsException.class);
        verifyNoInteractions(userWriter);
    }

    @Test
    void withdrawChangesStatusAndRevokesAllSessions() {
        User user = mock(User.class);
        when(userReader.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        var result = userService.withdraw(1L);

        verify(user).withdraw(result.withdrawnAt());
        verify(userWriter).save(user);
        verify(userSessionRevoker).revokeAll(1L);
        verify(processingConsentLedger).revokeForUser(1L);
        verify(recordingDeletionScheduler).scheduleAllForUser(1L, RecordingDeletionReason.USER_WITHDRAWN);
        verify(uploadIntentRegistry).expireForUser(1L);
    }

    @Test
    void withdrawRejectsAlreadyWithdrawnUser() {
        User user = mock(User.class);
        when(user.isWithdrawn()).thenReturn(true);
        when(userReader.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.withdraw(1L))
                .isInstanceOf(WithdrawalAlreadyProcessedException.class);
        verifyNoInteractions(
                userWriter,
                userSessionRevoker,
                processingConsentLedger,
                recordingDeletionScheduler,
                uploadIntentRegistry
        );
    }
}
