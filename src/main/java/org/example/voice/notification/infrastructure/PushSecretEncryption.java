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
    public void requireConfigured() { if (key.length != 32) throw unavailable(); }
    public String encrypt(PushDraft draft) {
        requireConfigured();
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD("VC-BE:push:v1".getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(canonical(draft));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + encrypted.length).put(nonce).put(encrypted).array());
        } catch (Exception e) { throw unavailable(); }
    }
    public String fingerprint(PushDraft draft) {
        requireConfigured();
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical(draft)));
        } catch (Exception e) { throw unavailable(); }
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
