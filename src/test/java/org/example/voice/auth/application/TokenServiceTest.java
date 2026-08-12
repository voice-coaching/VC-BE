package org.example.voice.auth.application;

import org.example.voice.auth.domain.entity.RefreshToken;
import org.example.voice.auth.domain.model.IssuedTokens;
import org.example.voice.auth.domain.model.TokenClaims;
import org.example.voice.auth.domain.port.RefreshTokenReader;
import org.example.voice.auth.domain.port.RefreshTokenWriter;
import org.example.voice.auth.domain.port.TokenProvider;
import org.example.voice.auth.exception.InvalidTokenException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.port.UserReader;
import org.example.voice.user.domain.type.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TokenServiceTest {
    private TokenProvider provider;
    private RefreshTokenReader reader;
    private RefreshTokenWriter writer;
    private UserReader users;
    private TokenService service;

    @BeforeEach
    void setUp() {
        provider = mock(TokenProvider.class); reader = mock(RefreshTokenReader.class);
        writer = mock(RefreshTokenWriter.class); users = mock(UserReader.class);
        service = new TokenService(provider, reader, writer, users);
    }

    @Test
    void refreshRotatesStoredHashSoOldTokenCannotBeReused() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        RefreshToken stored = RefreshToken.issue(1L, "old-hash", "old-session", now.plusHours(1), now);
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L); when(user.getRole()).thenReturn(UserRole.USER);
        when(provider.parseRefreshToken("old-token")).thenReturn(new TokenClaims(1L, UserRole.USER, "old-session", now.plusHours(1)));
        when(reader.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(stored));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(provider.issue(1L, UserRole.USER)).thenReturn(new IssuedTokens("new-access", "new-refresh", "new-session", now.plusDays(1)));
        when(reader.findByUserIdForUpdate(1L)).thenReturn(Optional.of(stored));

        IssuedTokens result = service.rotate("old-token");

        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        assertThat(stored.getTokenHash()).doesNotContain("new-refresh").hasSize(64);
        verify(writer).save(stored);
    }

    @Test
    void expiredStoredSessionIsDeletedAndReportedPrecisely() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        RefreshToken stored = RefreshToken.issue(1L, "old-hash", "old-session", now.minusSeconds(1), now.minusDays(1));
        when(provider.parseRefreshToken("old-token")).thenReturn(new TokenClaims(1L, UserRole.USER, "old-session", now.plusMinutes(1)));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(User.class)));
        when(reader.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.rotate("old-token"))
                .isInstanceOfSatisfying(InvalidTokenException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED));
        verify(writer).delete(stored);
    }
}
