package org.example.voice.auth.application;

import jakarta.persistence.EntityManager;
import org.example.voice.auth.domain.port.RefreshTokenReader;
import org.example.voice.auth.domain.port.TokenProvider;
import org.example.voice.auth.exception.InvalidTokenException;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TokenSessionIntegrationTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TokenService tokens;
    @Autowired TokenProvider provider;
    @Autowired RefreshTokenReader sessions;

    @Test
    void renewalKeepsInflightAccessValidButLogoutRevokesBothCredentials() {
        User user = user();
        var original = tokens.issueSession(user);
        var renewed = tokens.rotate(original.refreshToken());
        var before = provider.parseAccessToken(original.accessToken());
        var after = provider.parseAccessToken(renewed.accessToken());
        assertThat(after.sessionId()).isEqualTo(before.sessionId());
        assertThat(sessions.isActiveSession(user.getId(), before.sessionId(), now())).isTrue();
        assertThatThrownBy(() -> tokens.rotate(original.refreshToken())).isInstanceOf(InvalidTokenException.class);
        tokens.revoke(user.getId(), renewed.refreshToken());
        assertThat(sessions.isActiveSession(user.getId(), before.sessionId(), now())).isFalse();
        assertThat(sessions.isActiveSession(user.getId(), after.sessionId(), now())).isFalse();
        assertThatThrownBy(() -> tokens.rotate(renewed.refreshToken())).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void aNewLoginStillReplacesThePreviousLoginSession() {
        User user = user();
        var original = tokens.issueSession(user);
        var next = tokens.issueSession(user);
        assertThat(next.sessionId()).isNotEqualTo(original.sessionId());
        assertThat(sessions.isActiveSession(user.getId(), original.sessionId(), now())).isFalse();
        assertThat(sessions.isActiveSession(user.getId(), next.sessionId(), now())).isTrue();
        assertThatThrownBy(() -> tokens.rotate(original.refreshToken())).isInstanceOf(InvalidTokenException.class);
    }

    private User user() {
        return new TransactionTemplate(transactions).execute(status -> {
            var user = User.createLocal(UUID.randomUUID() + "@example.invalid", "hash", "session test", now());
            em.persist(user);
            return user;
        });
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
