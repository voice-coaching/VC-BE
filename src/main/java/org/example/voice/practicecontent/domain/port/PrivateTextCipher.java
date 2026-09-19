package org.example.voice.practicecontent.domain.port;

public interface PrivateTextCipher {
    void requireConfigured();
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
    String fingerprint(String plaintext);
}
