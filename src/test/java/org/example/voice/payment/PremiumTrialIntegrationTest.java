package org.example.voice.payment;

import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.example.voice.coaching.application.CoachingUsageService;
import org.example.voice.common.exception.*;
import org.example.voice.common.security.LoginUser;
import org.example.voice.payment.application.PremiumTrialService;
import org.example.voice.payment.domain.MembershipReader;
import org.example.voice.payment.domain.model.PremiumTrial;
import org.example.voice.payment.domain.port.PremiumTrialStore;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PremiumTrialIntegrationTest {
    @Autowired PremiumTrialService service;
    @Autowired PremiumTrialStore trials;
    @Autowired MembershipReader memberships;
    @Autowired CoachingUsageService coaching;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired MockMvc mvc;
    @MockitoBean Clock clock;
    final AtomicReference<Instant> now = new AtomicReference<>();
    long user;

    @BeforeEach void setup() {
        now.set(Instant.parse("2026-11-09T03:14:15Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenAnswer(i -> now.get());
        // H2 test schema only; production's exact 72-hour constraint is in Flyway V71.
        jdbc.execute("CREATE TABLE IF NOT EXISTS user_premium_trials(user_id bigint primary key references users(id) on delete cascade, starts_at timestamp with time zone not null, expires_at timestamp with time zone not null)");
        user = newUser();
    }

    @Test void viewingDoesNotGrantAndActivationPersistsExactly72HoursWithNoPayment() throws Exception {
        assertThat(memberships.read(user).type()).isEqualTo("free");
        assertThat(service.status(user).eligible()).isTrue();
        assertThat(trials.find(user)).isEmpty();
        var activated = service.activate(user);
        assertThat(activated.status()).isEqualTo("ACTIVE");
        assertThat(Duration.between(activated.startsAt(), activated.expiresAt())).isEqualTo(Duration.ofHours(72));
        assertThat(trials.find(user)).isPresent();
        assertThat(memberships.read(user).type()).isEqualTo("paid");
        assertThat(memberships.read(user).environment()).isEqualTo("TRIAL");
        assertThat(memberships.readAll(List.of(user)).get(user)).isEqualTo(memberships.read(user));
        assertThat(jdbc.queryForObject("select count(*) from payment_test_orders where user_id=?", Long.class, user)).isZero();
        assertThat(jdbc.queryForObject("select role from users where id=?", String.class, user)).isEqualTo("USER");
        now.set(now.get().plusSeconds(3600));
        assertThat(service.activate(user)).isEqualTo(activated);
    }

    @Test void exactExpiryReturnsFreeAndNeverRestartsTrial() {
        var activated = service.activate(user);
        now.set(activated.expiresAt().toInstant().minusNanos(1));
        assertThat(memberships.read(user).type()).isEqualTo("paid");
        now.set(activated.expiresAt().toInstant());
        assertThat(memberships.read(user).type()).isEqualTo("free");
        assertThat(memberships.readAll(List.of(user)).get(user).type()).isEqualTo("free");
        assertThat(service.status(user).status()).isEqualTo("EXPIRED");
        assertThatThrownBy(() -> service.activate(user)).isInstanceOfSatisfying(BaseException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PREMIUM_TRIAL_ALREADY_USED));
        assertThat(trials.find(user).orElseThrow().expiresAt()).isEqualTo(activated.expiresAt());
    }

    @Test void trialEntitlementIsUnlimitedOnlyInsideItsWindow() {
        var activated = service.activate(user);
        // Exercise the production entitlement adapter without migration-only usage ledger tables.
        var reader = new org.example.voice.coaching.infrastructure.JpaCoachingEntitlementReader(em,
                mock(org.example.voice.payment.domain.PaymentPolicy.class),
                mock(org.example.voice.payment.domain.port.TestMembershipGrantReader.class), trials);
        var window = reader.find(user, activated.startsAt()).orElseThrow();
        assertThat(window.limit()).isNull();
        assertThat(window.membershipType()).isEqualTo("paid");
        assertThat(window.environment()).isEqualTo("TRIAL");
        assertThat(window.active(activated.expiresAt())).isFalse();
        assertThat(reader.find(user, activated.expiresAt())).isEmpty();
        assertThat(reader.find(user, activated.startsAt().minusNanos(1))).isEmpty();
    }

    @Test void concurrentRequestsPersistOneGrantAndOneExpiration() throws Exception {
        var pool = Executors.newFixedThreadPool(6);
        try {
            var futures = new ArrayList<Future<PremiumTrialService.Status>>();
            for (int i = 0; i < 12; i++) futures.add(pool.submit(() -> service.activate(user)));
            var first = futures.getFirst().get(20, TimeUnit.SECONDS);
            for (var future : futures) assertThat(future.get(20, TimeUnit.SECONDS)).isEqualTo(first);
            assertThat(jdbc.queryForObject("select count(*) from user_premium_trials where user_id=?", Long.class, user)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void rollbackDoesNotConsumeTheBenefit() {
        new TransactionTemplate(manager).executeWithoutResult(tx -> { service.activate(user); tx.setRollbackOnly(); });
        assertThat(trials.find(user)).isEmpty();
        assertThat(service.status(user).eligible()).isTrue();
    }

    @Test void existingEntitlementsAndInactiveAccountsCannotClaim() {
        jdbc.update("insert into user_test_membership_grants(user_id, granted_at) values (?,?)", user, OffsetDateTime.now(clock));
        assertThat(service.status(user).status()).isEqualTo("INELIGIBLE");
        assertThatThrownBy(() -> service.activate(user)).isInstanceOfSatisfying(BaseException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PREMIUM_TRIAL_NOT_ELIGIBLE));
        assertThat(trials.find(user)).isEmpty();
        long suspended = newUser();
        jdbc.update("update users set status='SUSPENDED' where id=?", suspended);
        assertThatThrownBy(() -> service.activate(suspended)).isInstanceOfSatisfying(BaseException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_SUSPENDED));
        jdbc.update("update users set status='WITHDRAWN' where id=?", suspended);
        assertThatThrownBy(() -> service.activate(suspended)).isInstanceOfSatisfying(BaseException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_WITHDRAWN));
    }

    @Test void endpointsAreAuthenticatedOwnerScopedAndNotCached() throws Exception {
        mvc.perform(get("/api/users/me/premium-trial")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users/me/premium-trial")).andExpect(status().isUnauthorized());
        var auth = authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(user), null, List.of()));
        long other = newUser();
        mvc.perform(get("/api/users/me/premium-trial").with(auth)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.eligible").value(true));
        mvc.perform(post("/api/users/me/premium-trial").with(auth).contentType("application/json")
                        .content("{\"userId\":" + other + ",\"expiresAt\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertThat(trials.find(other)).isEmpty();
        assertThat(trials.find(user).orElseThrow().expiresAt()).isEqualTo(OffsetDateTime.now(clock).plusHours(72));
    }

    private long newUser() {
        return new TransactionTemplate(manager).execute(tx -> {
            var value = User.createSocial(null, "trial fixture", OffsetDateTime.now(clock));
            em.persist(value); em.flush(); return value.getId();
        });
    }
}
