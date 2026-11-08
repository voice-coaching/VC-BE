package org.example.voice.notification.infrastructure;

import org.example.voice.notification.domain.NotificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Device tokens are never written to logs or stored in plaintext. */
@Component
public class NativeFcmTokenCipher {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public NativeFcmTokenCipher(@Value("${notification.native-fcm.encryption-key:}") String configured) {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(configured); }
        catch (IllegalArgumentException error) { decoded = new byte[0]; }
        key = decoded;
    }

    public String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw unavailable(); }
    }

    public String encrypt(String token) {
        if (key.length != 32) throw unavailable();
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(nonce) + ":" + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception error) { throw unavailable(); }
    }

    public String decrypt(String value) {
        if (key.length != 32) throw unavailable();
        try {
            String[] parts = value.split(":", -1);
            if (parts.length != 3 || !"v1".equals(parts[0])) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception error) { throw unavailable(); }
    }

    private NotificationException unavailable() { return new NotificationException(503, "FCM_STORAGE_UNAVAILABLE"); }
}
