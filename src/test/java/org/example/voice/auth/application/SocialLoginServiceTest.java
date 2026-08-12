package org.example.voice.auth.application;

import org.example.voice.auth.domain.model.AuthSession;
import org.example.voice.auth.domain.model.SocialUserInfo;
import org.example.voice.auth.domain.port.SocialOAuthProvider;
import org.example.voice.auth.domain.type.OAuthProvider;
import org.example.voice.auth.exception.UnsupportedSocialProviderException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SocialLoginServiceTest {
    @Test
    void rejectsUnsupportedProviderBeforeCallingExternalProvider() {
        SocialOAuthProvider google = mock(SocialOAuthProvider.class);
        when(google.provider()).thenReturn(OAuthProvider.GOOGLE);
        SocialAccountService accounts = mock(SocialAccountService.class);
        SocialLoginService service = new SocialLoginService(List.of(google), accounts);

        assertThatThrownBy(() -> service.login("NAVER", "code", "redirect"))
                .isInstanceOf(UnsupportedSocialProviderException.class);
        verifyNoInteractions(accounts);
    }

    @Test
    void authenticatesExternallyBeforeCompletingTransactionalLogin() {
        SocialOAuthProvider google = mock(SocialOAuthProvider.class);
        SocialAccountService accounts = mock(SocialAccountService.class);
        SocialUserInfo profile = new SocialUserInfo("provider-id", "user@example.com", "nick");
        when(google.provider()).thenReturn(OAuthProvider.GOOGLE);
        when(google.authenticate("code", "redirect")).thenReturn(profile);
        when(accounts.completeLogin(OAuthProvider.GOOGLE, profile)).thenReturn(mock(AuthSession.class));

        new SocialLoginService(List.of(google), accounts).login("GOOGLE", "code", "redirect");

        var order = inOrder(google, accounts);
        order.verify(google).authenticate("code", "redirect");
        order.verify(accounts).completeLogin(OAuthProvider.GOOGLE, profile);
    }
}
