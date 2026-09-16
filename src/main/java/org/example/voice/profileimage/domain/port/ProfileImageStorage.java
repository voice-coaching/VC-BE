package org.example.voice.profileimage.domain.port;

public interface ProfileImageStorage {
    String imageUrl(String objectKey);
    void upload(String objectKey, byte[] png);
    void delete(String objectKey);
}
