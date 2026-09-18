package org.example.voice.auth.application;

import org.example.voice.auth.domain.entity.SocialAccount;
import org.example.voice.auth.domain.model.SocialUserInfo;
import org.example.voice.auth.domain.port.SocialAccountReader;
import org.example.voice.auth.domain.port.SocialAccountWriter;
import org.example.voice.auth.domain.type.OAuthProvider;
import org.example.voice.auth.exception.AuthException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.onboarding.domain.port.OnboardingProfileReader;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.port.UserReader;
import org.example.voice.user.domain.port.UserWriter;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SocialAccountServiceTest {

    @Test
    void socialLoginRejectsWithdrawnLinkedUserWithoutIssuingNewSession() {
        SocialAccountReader accounts = mock(SocialAccountReader.class);
        SocialAccountWriter accountWriter = mock(SocialAccountWriter.class);
        UserReader users = mock(UserReader.class);
        UserWriter userWriter = mock(UserWriter.class);
        TokenService tokens = mock(TokenService.class);
        OnboardingProfileReader onboarding = mock(OnboardingProfileReader.class);
        SocialAccountService service = new SocialAccountService(
                accounts, accountWriter, users, userWriter, tokens, onboarding);
        SocialAccount linked = SocialAccount.create(
                1L, OAuthProvider.NAVER, "naver-user", "user@example.com", OffsetDateTime.now());
        User user = mock(User.class);
        when(accounts.findByProviderAndProviderUserId(OAuthProvider.NAVER, "naver-user"))
                .thenReturn(Optional.of(linked));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(user.isWithdrawn()).thenReturn(true);

        assertThatThrownBy(() -> service.completeLogin(
                OAuthProvider.NAVER, new SocialUserInfo("naver-user", "user@example.com", "nick")))
                .isInstanceOfSatisfying(AuthException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_WITHDRAWN));

        verify(tokens, never()).issueSession(any());
        verify(user, never()).recordLogin(any());
        verifyNoInteractions(accountWriter, userWriter, onboarding);
    }
}
