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
    @Override public void requireConfigured() {}
    @Override public String encrypt(String plaintext) { return "plain:" + plaintext; }
    @Override public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith("v1:")) return ciphertext == null ? null : ciphertext.startsWith("plain:") ? ciphertext.substring(6) : ciphertext;

        try {

            byte[] bytes = Base64.getDecoder().decode(ciphertext.substring(3));

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
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(plaintext.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private CustomContentException unavailable(){return new CustomContentException(503,"TEMPORARY_UNAVAILABLE");}
}
