package org.example.voice.practicecontent.infrastructure;

import org.example.voice.practicecontent.domain.CustomContentException;
import org.example.voice.practicecontent.domain.port.PrivateTextCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PrivateTextEncryption implements PrivateTextCipher {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
    private static final byte[] AAD = "VC-BE:custom-content:v1".getBytes(StandardCharsets.UTF_8);
    public PrivateTextEncryption(@Value("${custom-content.encryption-key:}") String configured) {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(configured); } catch (IllegalArgumentException e) { decoded = new byte[0]; }
        key = decoded;
    }
    @Override public void requireConfigured() { if(key.length != 32) throw unavailable(); }
    @Override public String encrypt(String plaintext) {
        requireConfigured();
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE,nonce);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v1:"+Base64.getEncoder().encodeToString(ByteBuffer.allocate(12+encrypted.length).put(nonce).put(encrypted).array());
        } catch(Exception e) { throw unavailable(); }
    }
    @Override public String decrypt(String ciphertext) {
        requireConfigured();
        try {
            if (!ciphertext.startsWith("v1:")) throw unavailable();
            byte[] bytes = Base64.getDecoder().decode(ciphertext.substring(3));
            if(bytes.length < 28) throw unavailable();
            ByteBuffer data=ByteBuffer.wrap(bytes); byte[] nonce=new byte[12]; data.get(nonce);
            byte[] encrypted=new byte[data.remaining()]; data.get(encrypted);
            return new String(cipher(Cipher.DECRYPT_MODE,nonce).doFinal(encrypted),StandardCharsets.UTF_8);
        } catch(Exception e) { throw unavailable(); }
    }
    private Cipher cipher(int mode,byte[] nonce) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce)); cipher.updateAAD(AAD); return cipher;
    }
    @Override public String fingerprint(String plaintext) {
        requireConfigured();
        try {
            javax.crypto.Mac mac=javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(("custom-request:"+plaintext).getBytes(StandardCharsets.UTF_8)));
        } catch(Exception e) { throw unavailable(); }
    }
    private CustomContentException unavailable(){return new CustomContentException(503,"TEMPORARY_UNAVAILABLE");}
}
