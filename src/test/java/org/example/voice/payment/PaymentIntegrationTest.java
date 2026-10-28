package org.example.voice.payment;

import jakarta.persistence.EntityManager;
import org.example.voice.common.security.LoginUser;
import org.example.voice.payment.application.*;
import org.example.voice.payment.domain.*;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PaymentIntegrationTest.Policy.class)
class PaymentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PaymentTransactions transactions;
    @Autowired PaymentService payments;
    @Autowired MembershipReader memberships;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean PaymentGateway gateway;
    @MockitoBean WebhookAuthentication webhook;
    Long userId;
    @BeforeEach void createUser() {
        userId = tx().execute(status -> {
            var user = User.createLocal(UUID.randomUUID() + "@example.invalid", "hash", "tester", OffsetDateTime.now());
            em.persist(user); em.flush(); return user.getId();
        });
    }
    @Test void createVerifyAndProfileExposeOnlyServerVerifiedTestEntitlement() throws Exception {
        var order = transactions.create(userId, key());
        assertThat(memberships.read(userId).type()).isEqualTo("free");
        when(gateway.get(order.getId())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return snapshot(order, "PAID", 15000, 0);
        });
        mvc.perform(post("/api/payment-orders/{id}/verify", order.getId()).with(as(userId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.environment").value("TEST"));
        mvc.perform(get("/api/users/me").with(as(userId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.membershipType").value("paid"))
                .andExpect(jsonPath("$.data.membershipEnvironment").value("TEST"));
        var expiry = memberships.read(userId).expiresAt();
        payments.verify(userId, order.getId());
        assertThat(memberships.read(userId).expiresAt()).isEqualTo(expiry);
        assertThatThrownBy(() -> transactions.create(userId, key())).hasMessage("MEMBERSHIP_ALREADY_ACTIVE");
        assertThat(transactions.create(userId, order.getIdempotencyKey()).getId()).isEqualTo(order.getId());
    }
    @Test void httpOrderCreationIsFixedPriceIdempotentAndStillFreeBeforeApproval() throws Exception {
        var key = key();
        var first = mvc.perform(post("/api/payment-orders").with(as(userId)).header("Idempotency-Key", key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAmount").value(15000))
                .andExpect(jsonPath("$.data.currency").value("KRW"))
                .andExpect(jsonPath("$.data.storeId").value("store"))
                .andExpect(jsonPath("$.data.channelKey").value("channel"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        var second = mvc.perform(post("/api/payment-orders").with(as(userId)).header("Idempotency-Key", key))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first);
        var order = transactions.create(userId, key);
        mvc.perform(get("/api/payment-orders/{id}", order.getId()).with(as(userId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.orderId").value(order.getId()));
        mvc.perform(get("/api/users/me").with(as(userId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.membershipType").value("free"));
    }
    @Test void ownershipAndInvalidClientAmountCannotBypassServerChecks() throws Exception {
        var order = transactions.create(userId, key());
        mvc.perform(post("/api/payment-orders/{id}/verify", order.getId()).with(as(userId + 100000)))
                .andExpect(status().isNotFound());
        verifyNoInteractions(gateway);
        when(gateway.get(order.getId())).thenReturn(snapshot(order, "PAID", 1, 0));
        mvc.perform(post("/api/payment-orders/{id}/verify", order.getId()).with(as(userId)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_MISMATCH"));
        assertThat(memberships.read(userId).type()).isEqualTo("free");
        assertThat(transactions.owned(userId, order.getId()).getStatus()).isEqualTo("PENDING");
    }
    @Test void providerFailureLeavesOrderRetryableAndDoesNotGrantAccess() throws Exception {
        var order = transactions.create(userId, key());
        when(gateway.get(order.getId())).thenThrow(new PaymentException(502, "PAYMENT_PROVIDER_UNAVAILABLE"));
        mvc.perform(post("/api/payment-orders/{id}/verify", order.getId()).with(as(userId)))
                .andExpect(status().isBadGateway());
        assertThat(memberships.read(userId).type()).isEqualTo("free");
        assertThat(transactions.owned(userId, order.getId()).getStatus()).isEqualTo("PENDING");
    }
    @Test void cancellationWithdrawnAccountAndExpiryRemoveMembership() {
        var order = transactions.create(userId, key());
        transactions.apply(userId, order.getId(), snapshot(order, "PAID", 15000, 0), null, null);
        assertThat(memberships.read(userId).type()).isEqualTo("paid");
        transactions.apply(userId, order.getId(), snapshot(order, "CANCELLED", 15000, 15000), null, null);
        assertThat(memberships.read(userId).type()).isEqualTo("free");
        var second = transactions.create(userId, key());
        transactions.apply(userId, second.getId(), snapshot(second, "PAID", 15000, 0), null, null);
        tx().executeWithoutResult(s -> em.find(User.class, userId).withdraw(OffsetDateTime.now()));
        assertThat(memberships.read(userId).type()).isEqualTo("free");
        assertThatThrownBy(() -> transactions.create(userId, key())).hasMessage("PAYMENT_USER_INACTIVE");
        tx().executeWithoutResult(s -> {
            var user = User.createLocal(UUID.randomUUID() + "@example.invalid", "hash", "expired", OffsetDateTime.now());
            em.persist(user); em.flush();
            var old = OffsetDateTime.now().minusMonths(2);
            var expired = PaymentOrder.create(user.getId(), key(), old);
            expired.apply(new PaymentSnapshot(expired.getId(), "store", "channel", "TEST", "PAID", 15000, 0, "KRW", old, old), old);
            em.persist(expired);
            assertThat(memberships.read(user.getId()).type()).isEqualTo("free");
        });
    }
    @Test void duplicateWebhookAndConcurrentVerificationCreateOneGrant() throws Exception {
        var order = transactions.create(userId, key());
        var snapshot = snapshot(order, "PAID", 15000, 0);
        when(gateway.get(order.getId())).thenReturn(snapshot);
        when(webhook.verify(anyString(), anyString(), any(), any())).thenReturn(order.getId());
        var eventId = "event-" + UUID.randomUUID();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/payments/webhooks/portone").header("webhook-id", eventId).content("{}"))
                    .andExpect(status().isOk());
        }
        var expiry = memberships.read(userId).expiresAt();
        try (var pool = Executors.newFixedThreadPool(4)) {
            var calls = new ArrayList<Callable<PaymentOrder>>();
            for (int i = 0; i < 8; i++) calls.add(() -> payments.verify(userId, order.getId()));
            for (var result : pool.invokeAll(calls)) assertThat(result.get().getExpiresAt()).isEqualTo(expiry);
        }
        Long receipts = tx().execute(s -> em.createQuery("select count(r) from PaymentWebhookReceipt r where r.id=:id", Long.class)
                .setParameter("id", eventId).getSingleResult());
        assertThat(receipts).isEqualTo(1L);
        mvc.perform(post("/api/payments/webhooks/portone").header("webhook-id", eventId).content("{\"changed\":true}"))
                .andExpect(status().isConflict());
    }
    @Test void simultaneousCreateUsesUserLockAndOneIdempotencyRecord() throws Exception {
        var key = key();
        var ids = new HashSet<String>();
        try (var pool = Executors.newFixedThreadPool(4)) {
            var calls = new ArrayList<Callable<String>>();
            for (int i = 0; i < 8; i++) calls.add(() -> transactions.create(userId, key).getId());
            for (var result : pool.invokeAll(calls)) ids.add(result.get());
        }
        assertThat(ids).hasSize(1);
    }
    @Test void securityAndWebhookPayloadLimitsAreEnforced() throws Exception {
        mvc.perform(post("/api/payment-orders").header("Idempotency-Key", key())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/payment-orders/test-unknown")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/payments/webhooks/portone")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/payment-orders").with(as(userId)).header("Idempotency-Key", "short"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/payment-orders").with(as(userId)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
        mvc.perform(post("/api/payments/webhooks/portone").content("x".repeat(65537)))
                .andExpect(status().isPayloadTooLarge());
        when(webhook.verify(anyString(), any(), any(), any())).thenThrow(new PaymentException(401, "WEBHOOK_SIGNATURE_INVALID"));
        mvc.perform(post("/api/payments/webhooks/portone").content("{}")).andExpect(status().isUnauthorized());
    }
    private TransactionTemplate tx() { return new TransactionTemplate(transactionManager); }
    private String key() { return UUID.randomUUID().toString(); }
    private PaymentSnapshot snapshot(PaymentOrder order, String status, long amount, long cancelled) {
        return new PaymentSnapshot(order.getId(), "store", "channel", "TEST", status, amount, cancelled, "KRW",
                order.getCreatedAt(), OffsetDateTime.now());
    }
    private RequestPostProcessor as(Long id) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(id), null, List.of()));
    }
    @TestConfiguration static class Policy {
        @Bean @Primary PaymentPolicy testPolicy() {
            return new PaymentPolicy() {
                public boolean eligible(Long userId) { return true; }
                public CheckoutSettings checkout() { return new CheckoutSettings("store", "channel"); }
                public void validate(PaymentSnapshot payment) {
                    if (!"store".equals(payment.storeId()) || !"channel".equals(payment.channelKey()) || !"TEST".equals(payment.channelType()))
                        throw new PaymentException(409, "PAYMENT_CHANNEL_MISMATCH");
                }
            };
        }
    }
}
