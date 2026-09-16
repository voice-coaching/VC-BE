package org.example.voice.notification;

import jakarta.persistence.EntityManager;
import org.example.voice.common.security.LoginUser;
import org.example.voice.notification.application.NotificationService;
import org.example.voice.notification.domain.NotificationException;
import org.example.voice.notification.domain.entity.*;
import org.example.voice.notification.domain.model.NotificationData.*;
import org.example.voice.notification.domain.port.NotificationLifecycle;
import org.example.voice.user.application.UserService;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"notification.push.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "notification.reminders.enabled=false"})
@AutoConfigureMockMvc @Transactional
class NotificationIntegrationTest {
    @Autowired EntityManager em;
    @Autowired NotificationService service;
    @Autowired NotificationLifecycle lifecycle;
    @Autowired UserService users;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean Clock clock;
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-16T12:00:00Z"); // Wednesday, 21:00 Seoul
    private Long user, other;
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW.toInstant()); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var tx = new TransactionTemplate(transactions);
        user = tx.execute(s -> createUser()); other = tx.execute(s -> createUser());
    }
    private Long createUser() {
        var user = User.createLocal(UUID.randomUUID() + "@example.invalid", "hash", "notification", NOW.minusDays(1));
        em.persist(user); return user.getId();
    }
    @Test void defaultsAndPartialUpdatesPersistAndMarketingStaysDisabled() {
        var defaults = service.preferences(user);
        assertThat(defaults.practiceReminder().enabled()).isFalse();
        assertThat(defaults.marketing().enabled()).isFalse();
        service.patch(user, new Patch(new ReminderPatch(true, "21:00", List.of(Weekday.MON, Weekday.WED), "Asia/Seoul"), null));
        service.patch(user, new Patch(new ReminderPatch(null, "20:30", null, null), new Marketing(false)));
        em.flush(); em.clear();
        var saved = service.preferences(user);
        assertThat(saved.practiceReminder().enabled()).isTrue();
        assertThat(saved.practiceReminder().time()).isEqualTo("20:30");
        assertThat(saved.practiceReminder().daysOfWeek()).containsExactly(Weekday.MON, Weekday.WED);
        assertThat(service.preferences(other).practiceReminder().enabled()).isFalse();
        assertStatus(409, () -> service.patch(user, new Patch(null, new Marketing(true))));
    }
    @Test void invalidTimesZonesDaysAndEmptyPatchesAreRejected() {
        for (ReminderPatch patch : List.of(new ReminderPatch(true, "24:00", null, null),
                new ReminderPatch(true, null, List.of(), null), new ReminderPatch(true, null, List.of(Weekday.MON, Weekday.MON), null),
                new ReminderPatch(true, null, null, "Not/AZone"), new ReminderPatch(true, "9:00", null, null)))
            assertStatus(400, () -> service.patch(user, new Patch(patch, null)));
        assertStatus(400, () -> service.patch(user, new Patch(null, null)));
    }
    @Test void remindersRespectTimezoneWeekdayWindowAndDeduplicateLocalDate() {
        service.patch(user, new Patch(new ReminderPatch(true, "21:00", List.of(Weekday.WED), "Asia/Seoul"), null));
        assertThat(service.generateReminder(user)).isTrue();
        assertThat(service.generateReminder(user)).isFalse();
        service.patch(user, new Patch(new ReminderPatch(null, "12:00", null, "UTC"), null));
        assertThat(service.generateReminder(user)).isFalse();
        assertThat(service.list(user, false, 0, 20).unreadCount()).isEqualTo(1);
        assertThat(service.generateReminder(other)).isFalse();
        service.patch(other, new Patch(new ReminderPatch(true, "21:00", List.of(Weekday.MON), "Asia/Seoul"), null));
        assertThat(service.generateReminder(other)).isFalse();
        service.patch(other, new Patch(new ReminderPatch(true, "20:54", List.of(Weekday.WED), "Asia/Seoul"), null));
        assertThat(service.generateReminder(other)).isFalse();
    }
    @Test void dstGapAndOverlapProduceAtMostOneLocalDateEvent() {
        var preference = NotificationPreference.defaults(user, NOW);
        preference.patch(new Patch(new ReminderPatch(true, "02:30", List.of(Weekday.SUN), "America/New_York"), null), NOW);
        assertThat(preference.dueDate(OffsetDateTime.parse("2026-03-08T07:30:00Z"))).isEqualTo(LocalDate.of(2026, 3, 8));
        preference.patch(new Patch(new ReminderPatch(null, "01:30", null, null), null), NOW);
        assertThat(preference.dueDate(OffsetDateTime.parse("2026-11-01T05:30:00Z"))).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(preference.dueDate(OffsetDateTime.parse("2026-11-01T06:30:00Z"))).isNull();
    }
    @Test void inboxPaginationUnreadCountOwnershipAndReadRetries() {
        var a = notification(user, "a"); var b = notification(user, "b"); notification(other, "other");
        var first = service.list(user, false, 0, 1);
        assertThat(first.items().getFirst().id()).isEqualTo(b.getId());
        assertThat(first.hasNext()).isTrue(); assertThat(first.unreadCount()).isEqualTo(2);
        var read = service.read(user, b.getId());
        when(clock.instant()).thenReturn(NOW.plusMinutes(1).toInstant());
        assertThat(service.read(user, b.getId()).readAt()).isEqualTo(read.readAt());
        assertThat(service.list(user, true, 0, 20).items()).extracting(Item::id).containsExactly(a.getId());
        assertStatus(404, () -> service.read(other, a.getId()));
        assertThat(service.readAll(user).updatedCount()).isEqualTo(1);
        assertThat(service.readAll(user).updatedCount()).isZero();
        em.clear();
        assertThat(service.list(user, false, 0, 20).unreadCount()).isZero();
        assertThat(service.list(other, false, 0, 20).unreadCount()).isEqualTo(1);
        assertStatus(400, () -> service.list(user, false, Integer.MAX_VALUE, 100));
        assertStatus(400, () -> UserNotification.create(user, "PRACTICE_REMINDER", "title", "body", "//evil.invalid", "bad", NOW));
    }
    @Test void pushRegistrationIsEncryptedIdempotentAndOwnerScoped() {
        var draft = draft("one"); var created = service.register(user, draft, "request-1");
        assertThat(service.register(user, draft, "request-1")).isEqualTo(created);
        assertThat(service.register(user, draft, null).id()).isEqualTo(created.id());
        assertStatus(409, () -> service.register(user, draft("two"), "request-1"));
        assertStatus(409, () -> service.register(other, draft, null));
        assertStatus(404, () -> service.delete(other, created.id()));
        em.flush(); em.clear();
        var stored = em.find(PushSubscription.class, created.id());
        assertThat(stored.getEncryptedPayload()).startsWith("v1:").doesNotContain(draft.endpoint(), draft.auth(), draft.p256dh());
        assertThat(stored.getEndpointHash()).hasSize(64).doesNotContain("https");
        service.delete(user, created.id()); em.flush();
        assertStatus(404, () -> service.register(user, draft, "request-1"));
        assertThat(service.register(user, draft, "request-2").id()).isNotEqualTo(created.id());
    }
    @Test void pushExpiryRejectsStaleResponsesAndFreshRegistrationReactivates() {
        var created = service.register(user, draft("expiry"), null);
        lifecycle.recordPushResponse(created.id(), NOW.minusMinutes(1), 410); em.clear();
        assertThat(em.find(PushSubscription.class, created.id()).isActive()).isTrue();
        lifecycle.recordPushResponse(created.id(), NOW, 410); em.clear();
        assertThat(em.find(PushSubscription.class, created.id()).isActive()).isFalse();
        when(clock.instant()).thenReturn(NOW.plusMinutes(1).toInstant());
        assertThat(service.register(user, draft("expiry"), null).active()).isTrue();
        lifecycle.recordPushResponse(created.id(), NOW, 410); em.clear();
        assertThat(em.find(PushSubscription.class, created.id()).isActive()).isTrue();
    }
    @Test void registrationValidatesEndpointAndRealCurvePointAndLimitsDevices() {
        var valid = draft("validation");
        assertStatus(400, () -> service.register(user, new PushDraft("https://localhost/test", valid.p256dh(), valid.auth(), "Browser", null), null));
        assertStatus(400, () -> service.register(user, new PushDraft("https://fcm.googleapis.com.evil.invalid/test", valid.p256dh(), valid.auth(), "Browser", null), null));
        assertStatus(400, () -> service.register(user, new PushDraft(valid.endpoint(), Base64.getUrlEncoder().encodeToString(new byte[65]), valid.auth(), "Browser", null), null));
        for (int i = 0; i < 10; i++) service.register(user, draft("limit-" + i), null);
        assertStatus(429, () -> service.register(user, draft("limit-11"), null));
    }
    @Test void withdrawalErasesPreferencesInboxSubscriptionsAndReplayLedger() {
        service.patch(user, new Patch(new ReminderPatch(true, null, null, null), null));
        service.register(user, draft("withdraw"), "withdraw"); notification(user, "withdraw");
        users.withdraw(user); em.flush(); em.clear();
        for (String entity : List.of("NotificationPreference", "UserNotification", "PushSubscription", "PushRegistrationRequest"))
            assertThat(em.createQuery("select count(n) from " + entity + " n where n.userId=:user", Long.class).setParameter("user", user).getSingleResult()).isZero();
        assertThat(service.generateReminder(user)).isFalse();
        assertStatus(403, () -> service.preferences(user));
        org.springframework.test.util.ReflectionTestUtils.setField(em.find(User.class, other), "status", org.example.voice.user.domain.type.UserStatus.SUSPENDED);
        assertStatus(403, () -> service.preferences(other));
        assertThat(service.generateReminder(other)).isFalse();
    }
    @Test void httpContractsCoverAuthenticationDefaultsErrorsAndReadAll() throws Exception {
        var auth = authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(user), null, List.of()));
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me/notification-preferences").with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.practiceReminder.enabled").value(false)).andExpect(jsonPath("$.data.marketing.enabled").value(false));
        mvc.perform(patch("/api/users/me/notification-preferences").with(auth).contentType("application/json")
                .content("{\"practiceReminder\":{\"enabled\":true,\"daysOfWeek\":[\"WED\"]}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.practiceReminder.daysOfWeek[0]").value("WED"));
        mvc.perform(patch("/api/users/me/notification-preferences").with(auth).contentType("application/json")
                .content("{\"marketing\":{\"enabled\":true}}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MARKETING_CONSENT_REQUIRED"));
        mvc.perform(get("/api/notifications").with(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
        mvc.perform(get("/api/notifications").param("size", "0").with(auth)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/users/me/push-subscriptions").with(auth).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/notifications/99999999/read").with(auth)).andExpect(status().isNotFound());
        mvc.perform(post("/api/notifications/read-all").with(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.updatedCount").value(0));
    }
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRegistrationAndReminderGenerationAreSerialized() throws Exception {
        var tx = new TransactionTemplate(transactions);
        Long owner = tx.execute(s -> createUser());
        try (var executor = Executors.newFixedThreadPool(4)) {
            service.patch(owner, new Patch(new ReminderPatch(true, "21:00", List.of(Weekday.WED), "Asia/Seoul"), null));
            var start = new CountDownLatch(1);
            var tasks = new ArrayList<Future<Long>>();
            for (int i = 0; i < 4; i++) tasks.add(executor.submit(() -> { start.await(); return service.register(owner, draft("concurrent"), "concurrent").id(); }));
            start.countDown(); Set<Long> ids = new HashSet<>();
            for (var task : tasks) ids.add(task.get(20, TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
            var reminders = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 4; i++) reminders.add(executor.submit(() -> service.generateReminder(owner)));
            int created = 0; for (var task : reminders) if (task.get(20, TimeUnit.SECONDS)) created++;
            assertThat(created).isEqualTo(1); assertThat(service.list(owner, false, 0, 20).totalElements()).isEqualTo(1);
        } finally {
            tx.executeWithoutResult(s -> { for (Long id : List.of(owner, user, other)) { lifecycle.eraseForUser(id); em.createQuery("delete from User u where u.id=:id").setParameter("id", id).executeUpdate(); } });
        }
    }
    private UserNotification notification(Long user, String key) {
        var n = UserNotification.create(user, "PRACTICE_REMINDER", "연습 알림", "연습할 시간입니다.", "/home", key, NOW);
        em.persist(n); return n;
    }
    static PushDraft draft(String suffix) {
        // Standard P-256 generator is a valid uncompressed public point; no real browser credential.
        String point = "046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5";
        return new PushDraft("https://fcm.googleapis.com/fcm/send/test-" + suffix,
                Base64.getUrlEncoder().withoutPadding().encodeToString(HexFormat.of().parseHex(point)),
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]), "Test browser", "테스트 기기");
    }
    private void assertStatus(int status, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(NotificationException.class, e -> assertThat(e.status()).isEqualTo(status));
    }
}
