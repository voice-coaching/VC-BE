package org.example.voice.auth.application;

import org.example.voice.auth.domain.model.IssuedTokens;
import org.example.voice.auth.domain.port.PasswordHasher;
import org.example.voice.auth.exception.AuthException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.onboarding.domain.port.OnboardingProfileReader;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.port.UserReader;
import org.example.voice.user.domain.port.UserWriter;
import org.example.voice.user.domain.type.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private UserReader userReader;
    private UserWriter userWriter;
    private PasswordHasher passwordHasher;
    private TokenService tokenService;
    private OnboardingProfileReader onboardingReader;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userReader = mock(UserReader.class); userWriter = mock(UserWriter.class);
        passwordHasher = mock(PasswordHasher.class); tokenService = mock(TokenService.class);
        onboardingReader = mock(OnboardingProfileReader.class);
        service = new AuthService(userReader, userWriter, passwordHasher, tokenService, onboardingReader);
    }

    @Test
    void signupHashesPasswordAndIssuesSession() {
        User saved = mock(User.class);
        when(saved.getId()).thenReturn(1L); when(saved.getRole()).thenReturn(UserRole.USER);
        when(userWriter.save(any(User.class))).thenReturn(saved);
        when(passwordHasher.hash("Password123!")).thenReturn("encoded");
        when(tokenService.issueSession(saved)).thenReturn(new IssuedTokens("access", "refresh", "session", OffsetDateTime.now().plusDays(1)));
        when(tokenService.accessTokenSeconds()).thenReturn(600L);

        var result = service.signup("USER@EXAMPLE.COM", "Password123!", "nick", true, true);

        assertThat(result.accessToken()).isEqualTo("access");
        verify(passwordHasher).hash("Password123!");
        verify(userReader).existsByEmail("user@example.com");
    }

    @Test
    void signupRejectsDuplicateEmail() {
        when(userReader.existsByEmail("user@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.signup("user@example.com", "Password123!", "nick", true, true))
                .isInstanceOfSatisfying(AuthException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    void loginDoesNotRevealWhetherEmailOrPasswordWasWrong() {
        when(userReader.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login("missing@example.com", "wrong"))
                .isInstanceOfSatisfying(AuthException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        verifyNoInteractions(passwordHasher);
    }
}
