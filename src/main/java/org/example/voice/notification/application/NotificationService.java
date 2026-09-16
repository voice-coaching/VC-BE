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
        active(userId, true); positive(id);
        var item = store.notification(userId, id).orElseThrow(NotificationException::missing);
        item.markRead(now()); return item.view();
    }
    @Transactional
    public ReadAll readAll(Long userId) { active(userId, true); return new ReadAll(store.readAll(userId, now())); }

    @Transactional
    public Subscription register(Long userId, PushDraft draft, String key) {
        active(userId, true); validate(draft); validateKey(key); secrets.requireConfigured();
        String bodyHash = secrets.fingerprint(draft);
        if (key != null) {
            var old = store.request(userId, hash(key));
            if (old.isPresent()) {
                if (!old.get().getBodyHash().equals(bodyHash)) throw new NotificationException(409, "CONFLICT");
                store.subscription(userId, old.get().getSubscriptionId()).orElseThrow(NotificationException::missing);
                return old.get().view();
            }
        }
        String endpointHash = hash(draft.endpoint());
        var existing = store.subscriptionByHash(endpointHash);
        if (existing.isPresent() && !existing.get().getUserId().equals(userId)) throw new NotificationException(409, "CONFLICT");
        if (existing.isEmpty() && store.subscriptionCount(userId) >= 10) throw new NotificationException(429, "PUSH_SUBSCRIPTION_LIMIT");
        var now = now(); String encrypted = secrets.encrypt(draft);
        var subscription = existing.orElseGet(() -> PushSubscription.create(userId, endpointHash, encrypted, draft.deviceName(), now));
        if (existing.isPresent()) subscription.refresh(encrypted, draft.deviceName(), now);
        store.save(subscription);
        if (key != null) store.save(new PushRegistrationRequest(userId, hash(key), bodyHash, subscription, now));
        return subscription.view();
    }
    @Transactional
    public void delete(Long userId, Long id) {
        active(userId, true); positive(id);
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
        if (user.getStatus() != org.example.voice.user.domain.type.UserStatus.ACTIVE) throw new NotificationException(403, "FORBIDDEN");
        return user;
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS); }
    private void positive(Long id) { if (id == null || id <= 0) throw NotificationException.invalid(); }
    private void validateKey(String key) {
        if (key != null && (key.isEmpty() || key.length() > 128 || key.chars().anyMatch(c -> c < 33 || c > 126))) throw NotificationException.invalid();
    }
    private void validate(PushDraft draft) {
        if (draft == null) throw NotificationException.invalid();
        text(draft.endpoint(), 2048, true); text(draft.userAgent(), 512, true); text(draft.deviceName(), 100, false);
        try {
            URI uri = URI.create(draft.endpoint());
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || host == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getRawPath() == null || uri.getRawPath().length() < 2
                    || !(host.equals("fcm.googleapis.com") || host.equals("updates.push.services.mozilla.com")
                    || host.endsWith(".push.services.mozilla.com") || host.equals("web.push.apple.com")
                    || host.endsWith(".notify.windows.com"))) throw NotificationException.invalid();
            byte[] publicKey = decode(draft.p256dh(), 65), auth = decode(draft.auth(), 16);
            if (publicKey[0] != 4 || auth.length != 16) throw NotificationException.invalid();
            // Validate the uncompressed P-256 point without invoking an external provider or URL.
            BigInteger p = new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16);
            BigInteger b = new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16);
            BigInteger x = new BigInteger(1, Arrays.copyOfRange(publicKey, 1, 33));
            BigInteger y = new BigInteger(1, Arrays.copyOfRange(publicKey, 33, 65));
            if (x.compareTo(p) >= 0 || y.compareTo(p) >= 0 || !y.multiply(y).mod(p).equals(x.pow(3).subtract(x.multiply(BigInteger.valueOf(3))).add(b).mod(p)))
                throw NotificationException.invalid();
        } catch (IllegalArgumentException e) { throw NotificationException.invalid(); }
    }
    private byte[] decode(String value, int length) {
        if (value == null || value.length() > 100 || !value.matches("[A-Za-z0-9_-]+={0,2}")) throw NotificationException.invalid();
        byte[] result = Base64.getUrlDecoder().decode(value);
        if (result.length != length) throw NotificationException.invalid();
        return result;
    }
    private void text(String value, int max, boolean required) {
        if (value == null) { if (required) throw NotificationException.invalid(); return; }
        if (value.isBlank() || value.length() > max || value.codePoints().anyMatch(Character::isISOControl)) throw NotificationException.invalid();
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
