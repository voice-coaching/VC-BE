package org.example.voice.profileimage.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.profileimage.domain.ProfileImageException;
import org.example.voice.profileimage.domain.model.ProfileImageData;
import org.example.voice.profileimage.domain.port.ProfileImageProcessor;
import org.example.voice.profileimage.domain.port.ProfileImageStorage;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileImageService {
    private final ProfileImageTransactions transactions;
    private final ProfileImageProcessor processor;
    private final ProfileImageStorage storage;

    public ProfileImageData current(Long userId) { return transactions.current(userId); }
    public void delete(Long userId) { transactions.deleteCurrent(userId); }

    public ProfileImageData upload(Long userId, boolean replace, byte[] source, String fileName, String key) {
        String keyDigest = null;
        if (key != null) {
            if (!key.matches("[\\x21-\\x7e]{1,128}")) throw new ProfileImageException(400, "VALIDATION_ERROR");
            keyDigest = hash(key.getBytes(StandardCharsets.US_ASCII));
        }
        // Validate the user before spending time decoding untrusted bytes.
        transactions.current(userId);
        String name = safeName(fileName);
        byte[] png = processor.thumbnail(source);
        String objectKey = "profiles/" + UUID.randomUUID() + ".png";
        String digest = hash((hash(source) + ":" + name + ":" + replace).getBytes(StandardCharsets.UTF_8));
        var reservation = transactions.reserve(userId, replace, keyDigest, digest, objectKey,
                storage.imageUrl(objectKey), name, png.length);
        if (reservation.replay() != null) return reservation.replay();
        // The durable reservation is cleaned after its deadline even if this process dies.
        storage.upload(reservation.objectKey(), png);
        return transactions.activate(userId, reservation);
    }

    private String safeName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "image";
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) return "image";
        return name.length() > 200 ? name.substring(0, 200) : name;
    }
    private String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
