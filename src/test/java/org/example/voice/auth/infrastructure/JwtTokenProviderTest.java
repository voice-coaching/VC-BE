package org.example.voice.auth.infrastructure;

import org.example.voice.auth.exception.InvalidTokenException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.user.domain.type.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {
    private final JwtTokenProvider provider = new JwtTokenProvider(
            "test-secret-test-secret-test-secret-1234", "voice-test", 600, 1200);

    @Test
    void issuesTypedAccessAndRefreshTokens() {
        var issued = provider.issue(7L, UserRole.USER);
        assertThat(provider.parseAccessToken(issued.accessToken()).userId()).isEqualTo(7L);
        assertThat(provider.parseRefreshToken(issued.refreshToken()).role()).isEqualTo(UserRole.USER);
    }

    @Test
    void rejectsRefreshTokenAsAccessToken() {
        var issued = provider.issue(7L, UserRole.USER);
        assertThatThrownBy(() -> provider.parseAccessToken(issued.refreshToken()))
                .isInstanceOfSatisfying(InvalidTokenException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}
