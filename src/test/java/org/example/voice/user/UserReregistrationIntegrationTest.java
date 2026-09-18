package org.example.voice.user;

import jakarta.persistence.EntityManager;
import org.example.voice.auth.application.AuthService;
import org.example.voice.auth.domain.entity.SocialAccount;
import org.example.voice.auth.domain.type.OAuthProvider;
import org.example.voice.user.application.UserService;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserReregistrationIntegrationTest {

    @Autowired UserService userService;
    @Autowired AuthService authService;
    @Autowired EntityManager entityManager;

    @Test
    void withdrawalReleasesLocalAndSocialIdentitiesForANewAccount() {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-18T00:00:00Z");
        User withdrawn = User.createLocal("returning@example.com", "old-password-hash", "old-user", now);
        entityManager.persist(withdrawn);
        entityManager.flush();
        Long withdrawnUserId = withdrawn.getId();
        entityManager.persist(SocialAccount.create(
                withdrawnUserId, OAuthProvider.NAVER, "returning-naver-id", "returning@example.com", now));
        entityManager.flush();

        userService.withdraw(withdrawnUserId);
        entityManager.flush();
        entityManager.clear();

        User retainedHistoryOwner = entityManager.find(User.class, withdrawnUserId);
        assertThat(retainedHistoryOwner.isWithdrawn()).isTrue();
        assertThat(retainedHistoryOwner.getEmail()).isNull();
        assertThat(retainedHistoryOwner.getPassword()).isNull();
        assertThat(entityManager.createQuery(
                        "select count(account) from SocialAccount account where account.userId = :userId", Long.class)
                .setParameter("userId", withdrawnUserId)
                .getSingleResult()).isZero();

        var newSession = authService.signup(
                "returning@example.com", "NewPassword123!", "new-user", true, true);

        assertThat(newSession.user().getId()).isNotEqualTo(withdrawnUserId);
        assertThat(newSession.user().getEmail()).isEqualTo("returning@example.com");
        entityManager.persist(SocialAccount.create(
                newSession.user().getId(), OAuthProvider.NAVER, "returning-naver-id", "returning@example.com", now));
        entityManager.flush();
    }
}
