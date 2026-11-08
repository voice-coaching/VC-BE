package org.example.voice.notification.infrastructure;

import org.example.voice.notification.domain.NotificationException;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class NativeFcmTokenCipherTest {
    @Test void encryptsAndAuthenticatesTokens() {
        var cipher = new NativeFcmTokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
        String first = cipher.encrypt("registration-token");
        String second = cipher.encrypt("registration-token");
        assertNotEquals(first, second);
        assertFalse(first.contains("registration-token"));
        assertEquals("registration-token", cipher.decrypt(first));
        assertEquals(cipher.hash("registration-token"), cipher.hash("registration-token"));
        assertThrows(NotificationException.class, () -> cipher.decrypt(first + "invalid"));
    }

    @Test void requiresAProperEncryptionKey() {
        var cipher = new NativeFcmTokenCipher("");
        assertThrows(NotificationException.class, () -> cipher.encrypt("token"));
    }
}
