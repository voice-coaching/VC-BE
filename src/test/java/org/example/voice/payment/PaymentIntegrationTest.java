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
import tools.jackson.databind.ObjectMapper;
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
    @Autowired PaymentRecoveryService recovery;
    @Autowired PaymentCancellationService cancellations;
    @Autowired MembershipReader memberships;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean PaymentGateway gateway;
    @MockitoBean WebhookAuthentication webhook;
    Long userId;

    @Test void withdrawnUserCannotSendCancellationToProvider() {
        var order = transactions.create(userId, key());
        tx().executeWithoutResult(status -> em.find(User.class, userId).withdraw(OffsetDateTime.now()));
        assertThatThrownBy(() -> cancellations.cancel(userId, order.getId(), "request"))
                .hasMessage("PAYMENT_USER_INACTIVE");
        verifyNoInteractions(gateway);
    }

    @Test void concurrentFullCancellationCallsShareOneProviderCommand() throws Exception {
        var order = transactions.create(userId, key());
        var paid = snapshot(order, "PAID", 15000, 0);
        when(gateway.get(order.getId())).thenReturn(paid);
        when(gateway.cancel(eq(order.getId()), anyString(), anyString(), eq(15000L))).thenReturn("REQUESTED");
        var pool = Executors.newFixedThreadPool(4);
        try {
            var jobs = new ArrayList<Future<String>>();
            for (int i = 0; i < 4; i++) jobs.add(pool.submit(() -> cancellations.cancel(userId, order.getId(), "request").id()));
            var requestIds = new HashSet<String>();
            for (var job : jobs) requestIds.add(job.get(10, TimeUnit.SECONDS));
            assertThat(requestIds).hasSize(1);
        } finally { pool.shutdownNow(); }
        verify(gateway, times(1)).cancel(eq(order.getId()), anyString(), anyString(), eq(15000L));
        assertThat(transactions.owned(userId, order.getId()).getStatus()).isEqualTo("PAID");
    }

    @Test void fullCancellationIsOwnerScopedDurableAndNeverSentTwice() throws Exception {
        var order = transactions.create(userId, key());
        var paid = snapshot(order, "PAID", 15000, 0);
        var cancelled = snapshot(order, "CANCELLED", 15000, 15000);
        when(gateway.get(order.getId())).thenReturn(paid, cancelled);
        when(gateway.cancel(eq(order.getId()), anyString(), eq("customer request"), eq(15000L)))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    var count = tx().execute(status -> em.createQuery(
                            "select count(c) from PaymentCancellation c where c.orderId=:id", Long.class)
                            .setParameter("id", order.getId()).getSingleResult());
                    assertThat(count).isEqualTo(1);
                    return "SUCCEEDED";
                });
        mvc.perform(post("/api/payment-orders/{id}/cancel", order.getId()).with(as(userId))
                        .contentType("application/json").content("{\"reason\":\"customer request\",\"amount\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        assertThat(memberships.read(userId).type()).isEqualTo("free");
        assertThat(transactions.owned(userId, order.getId()).getStatus()).isEqualTo("CANCELLED");
        cancellations.cancel(userId, order.getId(), "customer request");
        verify(gateway, times(1)).cancel(eq(order.getId()), anyString(), anyString(), eq(15000L));
        assertThatThrownBy(() -> cancellations.cancel(userId + 999, order.getId(), "customer request"))
                .hasMessage("PAYMENT_ORDER_NOT_FOUND");
        mvc.perform(post("/api/payment-orders/{id}/cancel", order.getId()).contentType("application/json")
                        .content("{\"reason\":\"customer request\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void uncertainCancellationDoesNotResendAndWebhookCanReconcileIt() {
        var order = transactions.create(userId, key());
        var paid = snapshot(order, "PAID", 15000, 0);
        when(gateway.get(order.getId())).thenReturn(paid);
        when(gateway.cancel(eq(order.getId()), anyString(), anyString(), eq(15000L)))
                .thenThrow(new PaymentException(502, "PAYMENT_PROVIDER_UNAVAILABLE"));
        assertThatThrownBy(() -> cancellations.cancel(userId, order.getId(), "request"))
                .hasMessage("PAYMENT_CANCELLATION_UNCONFIRMED");
        assertThat(cancellations.cancel(userId, order.getId(), "request").status().name()).isEqualTo("UNKNOWN");
        assertThatThrownBy(() -> cancellations.cancel(userId, order.getId(), "different request"))
                .hasMessage("PAYMENT_CANCELLATION_CONFLICT");
        verify(gateway, times(1)).cancel(eq(order.getId()), anyString(), anyString(), anyLong());
        var cancelled = snapshot(order, "CANCELLED", 15000, 15000);
        transactions.apply(userId, order.getId(), cancelled, "cancel-event", "digest");
        when(gateway.get(order.getId())).thenReturn(cancelled);
        assertThat(cancellations.cancel(userId, order.getId(), "request").status().name()).isEqualTo("SUCCEEDED");
    }

    @Test void cancellationRejectsPartialCancellationWrongProviderAndInvalidReason() {
        var order = transactions.create(userId, key());
        assertThatThrownBy(() -> cancellations.cancel(userId, order.getId(), " "))
                .hasMessage("INVALID_CANCELLATION_REASON");
        assertThatThrownBy(() -> cancellations.cancel(userId, order.getId(), "line\nbreak"))
                .hasMessage("INVALID_CANCELLATION_REASON");
        verifyNoInteractions(gateway);
        when(gateway.get(order.getId())).thenReturn(new PaymentSnapshot(order.getId(), "store", "channel", "LIVE",
                "PAID", 15000, 0, "KRW", order.getCreatedAt(), OffsetDateTime.now()));
        assertThatThrownBy(() -> cancellations.cancel(userId, order.getId(), "request"))
                .hasMessage("PAYMENT_CHANNEL_MISMATCH");
        when(gateway.get(order.getId())).thenReturn(snapshot(order, "PARTIAL_CANCELLED", 15000, 100));
        assertThatThrownBy(() -> cancellations.cancel(userId, order.getId(), "request"))
                .hasMessage("PAYMENT_NOT_CANCELLABLE");
        verify(gateway, never()).cancel(anyString(), anyString(), anyString(), anyLong());
    }
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
    @Test void paymentHistoryIsOwnerScopedStableAndPagedAcrossAllStoredStates() throws Exception {
        var own = historyFixtures();
        var expected = own.stream().map(PaymentOrder::getId).sorted(Comparator.reverseOrder()).toList();
        var collected = new ArrayList<String>();
        for (int page = 0; page < 2; page++) {
            var body = mvc.perform(get("/api/users/me/payment-orders").with(as(userId))
                            .param("page", Integer.toString(page)).param("size", "2"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.result").value(true))
                    .andExpect(jsonPath("$.data.totalElements").value(4))
                    .andExpect(jsonPath("$.data.totalPages").value(2))
                    .andExpect(jsonPath("$.data.page").value(page))
                    .andExpect(jsonPath("$.data.size").value(2))
                    .andExpect(jsonPath("$.data.hasNext").value(page == 0))
                    .andReturn().getResponse().getContentAsString();
            var items = new ObjectMapper().readTree(body).path("data").path("items");
            assertThat(items.size()).isEqualTo(2);
            for (var item : items) {
                collected.add(item.path("orderId").asText());
                var fields = new HashSet<String>();
                item.propertyNames().forEach(fields::add);
                assertThat(fields).containsExactlyInAnyOrder("orderId", "product", "orderName", "totalAmount",
                        "currency", "status", "environment", "createdAt", "paidAt", "expiresAt");
                assertThat(item.path("environment").asText()).isEqualTo("TEST");
                assertThat(item.path("totalAmount").asLong()).isEqualTo(15000);
                assertThat(item.path("currency").asText()).isEqualTo("KRW");
                assertThat(item.path("product").asText()).isEqualTo(PaymentOrder.PRODUCT);
                assertThat(item.path("orderName").asText()).isEqualTo("SPEAK AI 월간 이용권 (테스트)");
                var order = own.stream().filter(p -> p.getId().equals(item.path("orderId").asText())).findFirst().orElseThrow();
                assertThat(item.path("status").asText()).isEqualTo(order.getStatus());
                assertThat(OffsetDateTime.parse(item.path("createdAt").asText()).toInstant()).isEqualTo(order.getCreatedAt().toInstant());
                if (order.getPaidAt() == null) {
                    assertThat(item.path("paidAt").isNull()).isTrue();
                    assertThat(item.path("expiresAt").isNull()).isTrue();
                } else {
                    assertThat(OffsetDateTime.parse(item.path("paidAt").asText()).toInstant()).isEqualTo(order.getPaidAt().toInstant());
                    assertThat(OffsetDateTime.parse(item.path("expiresAt").asText()).toInstant()).isEqualTo(order.getExpiresAt().toInstant());
                }
            }
        }
        assertThat(collected).containsExactlyElementsOf(expected);
        mvc.perform(get("/api/users/me/payment-orders").with(as(userId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20)).andExpect(jsonPath("$.data.items.length()").value(4));
        mvc.perform(get("/api/users/me/payment-orders").with(as(userId)).param("page", "2").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(4)).andExpect(jsonPath("$.data.hasNext").value(false));
        verifyNoInteractions(gateway, webhook);
    }

    @Test void historyOrdersByCreationBeforeIdAndAcceptsEmptyOrLargeValidPages() throws Exception {
        var older = transactions.create(userId, key());
        var newer = transactions.create(userId, key());
        tx().executeWithoutResult(s -> em.createQuery("update PaymentOrder p set p.createdAt=:old where p.id=:id")
                .setParameter("old", older.getCreatedAt().minusDays(1)).setParameter("id", older.getId()).executeUpdate());
        mvc.perform(get("/api/users/me/payment-orders").with(as(userId)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].orderId").value(newer.getId()))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mvc.perform(get("/api/users/me/payment-orders").with(as(userId)).param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2));
        mvc.perform(get("/api/users/me/payment-orders").with(as(userId)).param("page", "2147483647").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        // Existing orders from other users must not affect even the count for this user.
        Long emptyUser = tx().execute(s -> {
            var user = User.createLocal(UUID.randomUUID() + "@example.invalid", "hash", "empty", OffsetDateTime.now());
            em.persist(user); em.flush(); return user.getId();
        });
        mvc.perform(get("/api/users/me/payment-orders").with(as(emptyUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0)).andExpect(jsonPath("$.data.totalPages").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        verifyNoInteractions(gateway, webhook);
    }

    @Test void historyRequiresAuthenticationAndRejectsMalformedOrOverflowingPagination() throws Exception {
        mvc.perform(get("/api/users/me/payment-orders")).andExpect(status().isUnauthorized());
        for (var params : List.of(new String[]{"page", "-1"}, new String[]{"size", "0"},
                new String[]{"size", "101"}, new String[]{"page", "2147483647"},
                new String[]{"page", "2147483648"}, new String[]{"page", "abc"},
                new String[]{"size", "1.5"}, new String[]{"page", "1.0"})) {
            mvc.perform(get("/api/users/me/payment-orders").with(as(userId)).param(params[0], params[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(gateway, webhook);
    }

    private List<PaymentOrder> historyFixtures() {
        return tx().execute(s -> {
            var time = OffsetDateTime.now().minusMinutes(2).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            var result = new ArrayList<PaymentOrder>();
            for (var status : List.of("PENDING", "PAID", "FAILED", "CANCELLED")) {
                var order = PaymentOrder.create(userId, key(), time);
                if (!status.equals("PENDING")) {
                    if (status.equals("CANCELLED")) order.apply(snapshot(order, "PAID", 15000, 0), OffsetDateTime.now());
                    order.apply(snapshot(order, status, 15000, status.equals("CANCELLED") ? 15000 : 0), OffsetDateTime.now());
                }
                em.persist(order); result.add(order);
            }
            var other = User.createLocal(UUID.randomUUID() + "@example.invalid", "hash", "other", time);
            em.persist(other); em.flush();
            em.persist(PaymentOrder.create(other.getId(), key(), time.plusMinutes(1)));
            return result;
        });
    }
    @Test void recoveryRepairsMissingClientAndWebhookWithoutExtendingGrantAndRejectsMismatch() {
        var order = transactions.create(userId, key());
        var mismatch = transactions.create(userId, key());
        when(gateway.get(mismatch.getId())).thenReturn(snapshot(mismatch, "PAID", 1, 0));
        when(gateway.get(order.getId())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return snapshot(order, "PAID", 15000, 0);
        });
        // Other tests may leave pending orders; sweep through all keyset pages.
        sweep(order.getId(), "PAID");
        for (int i = 0; i < 10; i++) recovery.recover();
        assertThat(transactions.owned(userId, mismatch.getId()).getStatus()).isEqualTo("PENDING");
        assertThat(transactions.owned(userId, mismatch.getId()).getExpiresAt()).isNull();
        var expiry = memberships.read(userId).expiresAt();
        assertThat(expiry).isNotNull();
        when(gateway.get(order.getId())).thenReturn(snapshot(order, "PAID", 1, 0));
        for (int i = 0; i < 10; i++) recovery.recover();
        assertThat(transactions.owned(userId, order.getId()).getExpiresAt()).isEqualTo(expiry);
        when(gateway.get(order.getId())).thenReturn(snapshot(order, "CANCELLED", 15000, 15000));
        sweep(order.getId(), "CANCELLED");
        assertThat(memberships.read(userId).type()).isEqualTo("free");
        clearInvocations(gateway);
        for (int i = 0; i < 10; i++) recovery.recover();
        verify(gateway, never()).get(order.getId());
    }

    @Test void recoveryCandidateQueryPaginatesAndExcludesExpiredFailedAndCancelledOrders() {
        var ids = tx().execute(s -> {
            var now = OffsetDateTime.now();
            var pending = PaymentOrder.create(userId, key(), now);
            var paid = PaymentOrder.create(userId, key(), now);
            paid.apply(snapshot(paid, "PAID", 15000, 0), now);
            var failed = PaymentOrder.create(userId, key(), now);
            failed.apply(snapshot(failed, "FAILED", 15000, 0), now);
            var cancelled = PaymentOrder.create(userId, key(), now);
            cancelled.apply(snapshot(cancelled, "CANCELLED", 15000, 15000), now);
            var old = now.minusMonths(2);
            var expired = PaymentOrder.create(userId, key(), old);
            expired.apply(new PaymentSnapshot(expired.getId(), "store", "channel", "TEST", "PAID", 15000, 0, "KRW", old, old), old);
            for (var p : List.of(pending, paid, failed, cancelled, expired)) em.persist(p);
            return List.of(pending.getId(), paid.getId(), failed.getId(), cancelled.getId(), expired.getId());
        });
        var selected = new ArrayList<String>();
        String after = "";
        while (true) {
            var page = transactions.recoveryCandidates(after, 1);
            if (page.isEmpty()) break;
            assertThat(page).hasSize(1);
            selected.add(page.getFirst().orderId());
            after = page.getLast().orderId();
        }
        assertThat(selected).isSorted().doesNotHaveDuplicates().contains(ids.get(0), ids.get(1))
                .doesNotContain(ids.get(2), ids.get(3), ids.get(4));
    }

    private void sweep(String orderId, String expected) {
        for (int i = 0; i < 10 && !transactions.owned(userId, orderId).getStatus().equals(expected); i++) recovery.recover();
        assertThat(transactions.owned(userId, orderId).getStatus()).isEqualTo(expected);
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
