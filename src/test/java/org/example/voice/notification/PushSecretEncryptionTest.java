package org.example.voice.notification;

import org.example.voice.notification.domain.NotificationException;
import org.example.voice.notification.infrastructure.PushSecretEncryption;
import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PushSecretEncryptionTest {
    @Test void randomNoncesProtectPayloadAndRoundTripPreservesAllFields() throws Exception {
        var codec = new PushSecretEncryption(Base64.getEncoder().encodeToString(new byte[32]));
        var draft = NotificationIntegrationTest.draft("cipher");
        String encrypted = codec.encrypt(draft);
        assertThat(codec.encrypt(draft)).isNotEqualTo(encrypted);
        assertThat(codec.fingerprint(draft)).isEqualTo(codec.fingerprint(draft));
        byte[] bytes = Base64.getDecoder().decode(encrypted.substring(3));
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(new byte[32], "AES"), new GCMParameterSpec(128, Arrays.copyOfRange(bytes, 0, 12)));
        cipher.updateAAD("VC-BE:push:v1".getBytes(StandardCharsets.UTF_8));
        try (var input = new java.io.DataInputStream(new java.io.ByteArrayInputStream(cipher.doFinal(Arrays.copyOfRange(bytes, 12, bytes.length))))) {
            for (String value : List.of(draft.endpoint(), draft.p256dh(), draft.auth(), draft.userAgent(), draft.deviceName()))
                assertThat(new String(input.readNBytes(input.readInt()), StandardCharsets.UTF_8)).isEqualTo(value);
        }
    }
    @Test void missingKeyNeverFallsBackToPlaintext() {
        assertThatThrownBy(() -> new PushSecretEncryption("").encrypt(NotificationIntegrationTest.draft("missing")))
                .isInstanceOfSatisfying(NotificationException.class, e -> assertThat(e.status()).isEqualTo(503));
    }
}
