package org.example.voice.notification.infrastructure;

import org.example.voice.notification.domain.NotificationException;
import org.example.voice.notification.domain.model.NotificationData.PushDraft;
import org.example.voice.notification.domain.port.PushSecretCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

@Component
public class PushSecretEncryption implements PushSecretCodec {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
    public PushSecretEncryption(@Value("${notification.push.encryption-key:}") String configured) {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(configured); } catch (IllegalArgumentException e) { decoded = new byte[0]; }
        key = decoded;
    }
    public void requireConfigured() {}
    public String encrypt(PushDraft draft) {
        try { return "plain:" + Base64.getEncoder().encodeToString(canonical(draft)); }
        catch (java.io.IOException error) { throw unavailable(); }
    }
    public String fingerprint(PushDraft draft) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical(draft))); }
        catch (Exception error) { throw unavailable(); }
    }
    private byte[] canonical(PushDraft draft) throws java.io.IOException {
        var out = new java.io.ByteArrayOutputStream();
        try (var data = new java.io.DataOutputStream(out)) {
            for (String value : new String[]{draft.endpoint(), draft.p256dh(), draft.auth(), draft.userAgent(), draft.deviceName()}) {
                if (value == null) data.writeInt(-1);
                else { byte[] bytes = value.getBytes(StandardCharsets.UTF_8); data.writeInt(bytes.length); data.write(bytes); }
            }
        }
        return out.toByteArray();
    }
    private NotificationException unavailable() { return new NotificationException(503, "PUSH_STORAGE_UNAVAILABLE"); }
}
