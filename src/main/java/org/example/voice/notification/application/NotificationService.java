package org.example.voice.notification.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.notification.domain.NotificationException;
import org.example.voice.notification.domain.entity.*;
import org.example.voice.notification.domain.model.NotificationData.*;
import org.example.voice.notification.domain.port.*;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.port.UserReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.net.URI;
import java.math.BigInteger;
import java.util.*;

@Service @RequiredArgsConstructor
public class NotificationService {
    private final NotificationStore store;
    private final UserReader users;
    private final PushSecretCodec secrets;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Preferences preferences(Long userId) {
        User user = active(userId, false);
        return store.preferences(userId).orElseGet(() -> NotificationPreference.defaults(userId, user.getCreatedAt())).view();
    }
    @Transactional
    public Preferences patch(Long userId, Patch patch) {
        User user = active(userId, true);
        var preference = store.preferences(userId).orElseGet(() -> NotificationPreference.defaults(userId, user.getCreatedAt()));
        preference.patch(patch, now()); store.save(preference); return preference.view();
    }
    @Transactional(readOnly = true)
    public Page list(Long userId, boolean unreadOnly, int page, int size) {
        active(userId, false);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) throw NotificationException.invalid();
        return store.list(userId, unreadOnly, page, size);
    }
    @Transactional
    public Item read(Long userId, Long id) {
        active(userId, true);
        var item = store.notification(userId, id).orElseThrow(NotificationException::missing);
        item.markRead(now()); return item.view();
    }
    @Transactional
    public ReadAll readAll(Long userId) { active(userId, true); return new ReadAll(store.readAll(userId, now())); }

    @Transactional
    public Subscription register(Long userId, PushDraft draft, String key) {
        active(userId, true);
        String bodyHash = secrets.fingerprint(draft);
        if (key != null) {
            var old = store.request(userId, hash(key));

        }
        String endpointHash = hash(draft.endpoint());
        var existing = store.subscriptionByHash(endpointHash);

        var now = now(); String encrypted = secrets.encrypt(draft);
        var subscription = existing.orElseGet(() -> PushSubscription.create(userId, endpointHash, encrypted, draft.deviceName(), now));
        if (existing.isPresent()) subscription.refresh(encrypted, draft.deviceName(), now);
        store.save(subscription);
        if (key != null) store.save(new PushRegistrationRequest(userId, hash(key), bodyHash, subscription, now));
        return subscription.view();
    }
    @Transactional
    public void delete(Long userId, Long id) {
        active(userId, true);
        store.delete(store.subscription(userId, id).orElseThrow(NotificationException::missing));
    }
    @Transactional
    public boolean generateReminder(Long userId) {
        var user = users.findByIdForUpdate(userId);
        if (user.isEmpty() || user.get().getStatus() != org.example.voice.user.domain.type.UserStatus.ACTIVE) return false;
        var preferences = store.preferences(userId);
        if (preferences.isEmpty()) return false;
        var now = now(); var date = preferences.get().dueDate(now);
        if (date == null || store.hasEvent(userId, "practice:" + date)) return false;
        store.save(UserNotification.reminder(userId, date, now)); return true;
    }
    private User active(Long userId, boolean lock) {
        var user = (lock ? users.findByIdForUpdate(userId) : users.findById(userId)).orElseThrow(NotificationException::missing);

        return user;
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS); }

    private byte[] decode(String value, int length) {

        byte[] result = Base64.getUrlDecoder().decode(value);

        return result;
    }
    private void text(String value, int max, boolean required) {
        if (value == null) {  return; }

    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
