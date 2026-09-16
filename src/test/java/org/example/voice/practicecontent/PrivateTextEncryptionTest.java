package org.example.voice.practicecontent;

import org.example.voice.practicecontent.infrastructure.PrivateTextEncryption;
import org.example.voice.practicecontent.domain.CustomContentException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PrivateTextEncryptionTest {
    @Test void authenticatedEncryptionRejectsTamperingAndUsesRandomNonce(){
        var cipher=new PrivateTextEncryption("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        String first=cipher.encrypt("검증용 원고");
        assertThat(cipher.encrypt("검증용 원고")).isNotEqualTo(first);
        assertThat(cipher.decrypt(first)).isEqualTo("검증용 원고");
        byte[] raw=java.util.Base64.getDecoder().decode(first.substring(3));raw[raw.length-1]^=1;
        assertThatThrownBy(()->cipher.decrypt("v1:"+java.util.Base64.getEncoder().encodeToString(raw))).isInstanceOf(CustomContentException.class);
    }
    @Test void missingOrInvalidKeyNeverFallsBackToPlaintext(){
        for(String key:new String[]{"","invalid","YWJj"})
            assertThatThrownBy(()->new PrivateTextEncryption(key).encrypt("원고")).isInstanceOfSatisfying(CustomContentException.class,e->assertThat(e.status()).isEqualTo(503));
    }
}
