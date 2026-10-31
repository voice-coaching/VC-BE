package org.example.voice.payment;

import jakarta.persistence.EntityManager;
import org.example.voice.common.security.LoginUser;
import org.example.voice.payment.application.*;
import org.example.voice.payment.domain.*;
import org.example.voice.payment.infrastructure.JpaPaymentRequestQuota;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"payment.test.enabled=true", "PORTONE_STORE_ID=store", "PORTONE_TEST_CHANNEL_KEY=channel",
        "PORTONE_API_SECRET=fixture-secret", "PORTONE_WEBHOOK_SECRET=dGVzdC13ZWJob29rLXNpZ25hdHVyZS1rZXk="})
@AutoConfigureMockMvc
class PaymentRateLimitIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired PaymentQuotaTransactions quota;
    @Autowired PaymentTransactions orders;
    @MockitoBean Clock clock;
    @MockitoBean PaymentGateway gateway;
    @MockitoBean WebhookAuthentication webhook;
    final AtomicReference<Instant> now = new AtomicReference<>();
    Long userId;

    @BeforeEach void setup() {
        now.set(Instant.parse("2026-10-29T00:00:00Z"));
        when(clock.instant()).thenAnswer(invocation -> now.get());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        userId = user();
    }

    @Test void createCountsIdempotentRetriesAndKeepsOtherMembersAndOperationsIndependent() throws Exception {
        tx().executeWithoutResult(s -> em.persist(
                org.example.voice.payment.domain.entity.TestMembershipGrant.create(userId, OffsetDateTime.now())));
        String key = UUID.randomUUID().toString();
        for (int i = 0; i < 5; i++) mvc.perform(post("/api/payment-orders").with(as(userId)).header("Idempotency-Key", key))
                .andExpect(status().isOk());
        mvc.perform(post("/api/payment-orders").with(as(userId)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Origin", "http://localhost:3000").header("X-Forwarded-For", "198.51.100.77"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("PAYMENT_RATE_LIMITED"))
                .andExpect(jsonPath("$.data").isEmpty()).andExpect(header().string("Retry-After", "60"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Access-Control-Expose-Headers", org.hamcrest.Matchers.containsString("Retry-After")));
        Long count = tx().execute(s -> em.createQuery("select count(o) from PaymentOrder o where o.userId=:u", Long.class)
                .setParameter("u", userId).getSingleResult());
        assertThat(count).isEqualTo(1);
        mvc.perform(get("/api/users/me/payment-orders").with(as(userId))).andExpect(status().isOk());
        mvc.perform(post("/api/payment-orders").with(as(user())).header("Idempotency-Key", key)).andExpect(status().isOk());
        now.set(now.get().plusMillis(59500));
        mvc.perform(post("/api/payment-orders").with(as(userId)).header("Idempotency-Key", key))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "1"));
        now.set(now.get().plusMillis(500));
        mvc.perform(post("/api/payment-orders").with(as(userId)).header("Idempotency-Key", key)).andExpect(status().isOk());
        verifyNoInteractions(gateway);
    }

    @Test void failedVerificationConsumesQuotaBeforeProviderAndWebhooksStillReconcile() throws Exception {
        var order = orders.create(userId, UUID.randomUUID().toString());
        when(gateway.get(order.getId())).thenThrow(new PaymentException(502, "PAYMENT_PROVIDER_UNAVAILABLE"));
        for (int i = 0; i < 10; i++) mvc.perform(post("/api/payment-orders/{id}/verify", order.getId()).with(as(userId)))
                .andExpect(status().isBadGateway());
        mvc.perform(post("/api/payment-orders/{id}/verify", order.getId()).with(as(userId)))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        verify(gateway, times(10)).get(order.getId());
        when(webhook.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(order.getId());
        var paidAt = OffsetDateTime.ofInstant(now.get(), ZoneOffset.UTC);
        doReturn(new PaymentSnapshot(order.getId(), "store", "channel", "TEST", "PAID",
                15000, 0, "KRW", paidAt, paidAt)).when(gateway).get(order.getId());
        mvc.perform(post("/api/payments/webhooks/portone").contentType("application/json").content("{}")
                .header("webhook-id", "quota-webhook").header("webhook-signature", "fixture").header("webhook-timestamp", "fixture"))
                .andExpect(status().isOk());
        assertThat(orders.owned(userId, order.getId()).getStatus()).isEqualTo("PAID");
        // Internal provider reconciliation is not a client endpoint and must remain available.
        mvc.perform(get("/api/users/me").with(as(userId))).andExpect(status().isOk());
    }

    @Test void cancelAndRefundShareQuotaAndRejectedBusinessAttemptsDoNotRollItBack() throws Exception {
        for (int i = 0; i < 5; i++) mvc.perform(post("/api/payment-orders/missing/" + (i % 2 == 0 ? "cancel" : "refund-requests"))
                        .with(as(userId)).contentType("application/json").content("{\"reason\":\"test\"}"))
                .andExpect(status().isNotFound());
        for (String path : List.of("cancel", "refund-requests")) mvc.perform(post("/api/payment-orders/another/" + path)
                        .with(as(userId)).contentType("application/json").content("{\"reason\":\"test\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        verifyNoInteractions(gateway);
    }

    @Test void threeReadRoutesShareQuotaWithoutAffectingProfileOrPreflight() throws Exception {
        for (int i = 0; i < 60; i++) {
            var request = switch (i % 3) {
                case 0 -> get("/api/payment-orders/missing");
                case 1 -> get("/api/users/me/payment-orders");
                default -> get("/api/refund-requests/00000000-0000-0000-0000-000000000000");
            };
            mvc.perform(request.with(as(userId))).andExpect(status().is(i % 3 == 1 ? 200 : 404));
        }
        for (String path : List.of("/api/payment-orders/another", "/api/users/me/payment-orders", "/api/refund-requests/another"))
            mvc.perform(get(path).with(as(userId))).andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/users/me").with(as(userId))).andExpect(status().isOk());
        mvc.perform(options("/api/users/me/payment-orders").header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")).andExpect(status().isOk());
        verifyNoInteractions(gateway);
    }

    @Test void unauthenticatedCallsNeverAllocateQuotaAndInactiveMembersAllocateNone() throws Exception {
        Long before = rows();
        for (int i = 0; i < 7; i++) mvc.perform(post("/api/payment-orders").header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
        assertThat(rows()).isEqualTo(before);
        tx().executeWithoutResult(s -> em.find(User.class, userId).withdraw(OffsetDateTime.now()));
        mvc.perform(post("/api/payment-orders").with(as(userId)).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        assertThat(rows()).isEqualTo(before);
        verifyNoInteractions(gateway);
    }

    @Test void concurrentFirstRequestsCannotExceedQuotaAndIndependentAdaptersShareTheSameState() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            var start = new CountDownLatch(1);
            var requests = new ArrayList<Future<Long>>();
            for (int i = 0; i < 18; i++) requests.add(pool.submit(() -> {
                start.await();
                return quota.consume(userId, PaymentOperation.CREATE);
            }));
            start.countDown();
            int accepted = 0;
            for (var request : requests) {
                long retry = request.get(15, TimeUnit.SECONDS);
                assertThat(retry).isIn(0L, 60L);
                if (retry == 0) accepted++;
            }
            assertThat(accepted).isEqualTo(5);
        } finally { pool.shutdownNow(); }
        // A new adapter has no process-local allowance to reset on restart.
        var independent = new JpaPaymentRequestQuota(em, clock);
        assertThat(tx().<Long>execute(s -> independent.consume(userId, PaymentOperation.CREATE))).isEqualTo(60L);
        now.set(now.get().plusSeconds(60));
        assertThat(quota.consume(userId, PaymentOperation.CREATE)).isZero();
        assertThat(tx().<Long>execute(s -> em.createQuery("select count(q) from PaymentQuota q where q.userId=:u", Long.class)
                .setParameter("u", userId).getSingleResult())).isEqualTo(1L);
    }

    @Test void quotaLockTimeoutReturns503AndDoesNotExecutePaymentBusinessWork() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var holder = pool.submit(() -> tx().executeWithoutResult(s -> {
                em.find(User.class, userId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                locked.countDown();
                try { release.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var response = pool.submit(() -> mvc.perform(post("/api/payment-orders").with(as(userId))
                            .header("Idempotency-Key", UUID.randomUUID().toString()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("PAYMENT_RATE_LIMIT_UNAVAILABLE")));
            response.get(6, TimeUnit.SECONDS);
            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); pool.shutdownNow(); }
        assertThat(tx().<Long>execute(s -> em.createQuery("select count(o) from PaymentOrder o where o.userId=:u", Long.class)
                .setParameter("u", userId).getSingleResult())).isZero();
        verifyNoInteractions(gateway);
    }

    private Long rows() { return tx().execute(s -> em.createQuery("select count(q) from PaymentQuota q", Long.class).getSingleResult()); }
    private Long user() { return tx().execute(s -> {
        var user = User.createSocial(null, "quota fixture", OffsetDateTime.now());
        em.persist(user); em.flush(); return user.getId();
    }); }
    private TransactionTemplate tx() { return new TransactionTemplate(manager); }
    private RequestPostProcessor as(Long id) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(id), null, List.of()));
    }
}
