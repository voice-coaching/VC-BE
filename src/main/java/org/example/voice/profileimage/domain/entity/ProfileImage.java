package org.example.voice.profileimage.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.profileimage.domain.model.ProfileImageData;
import org.example.voice.profileimage.domain.ProfileImageException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "profile_images", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "key_digest"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "object_key", nullable = false, unique = true, length = 100) private String objectKey;
    @Column(name = "image_url", nullable = false, length = 2048) private String imageUrl;
    @Column(name = "original_file_name", nullable = false) private String originalFileName;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "key_digest", length = 64) private String keyDigest;
    @Column(name = "request_digest", nullable = false, length = 64) private String requestDigest;
    @Column(nullable = false, length = 20) private String state;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "activated_at") private OffsetDateTime activatedAt;
    @Column(name = "cleanup_after", nullable = false) private OffsetDateTime cleanupAfter;

    public static ProfileImage reserve(Long userId, String objectKey, String imageUrl, String fileName,
                                       long size, String keyDigest, String digest, OffsetDateTime now) {
        var image = new ProfileImage();
        image.userId = userId; image.objectKey = objectKey; image.imageUrl = imageUrl;
        image.originalFileName = fileName; image.sizeBytes = size; image.keyDigest = keyDigest;
        image.requestDigest = digest; image.state = "UPLOADING";
        image.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        image.cleanupAfter = now.plusMinutes(10);
        return image;
    }
    public void activate(OffsetDateTime now) {
        if (!state.equals("UPLOADING") || !cleanupAfter.isAfter(now))
            throw new ProfileImageException(409, "CONFLICT");
        state = "ACTIVE";
        activatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }
    public void retire(OffsetDateTime now) {
        state = "DELETE_PENDING";
        // Allow any bounded in-flight storage upload to finish before cleanup.
        cleanupAfter = now.plusMinutes(10);
    }
    public void deleted() { state = "DELETED"; }
    public void claimDeletion(OffsetDateTime now) { state = "DELETE_PENDING"; cleanupAfter = now.plusMinutes(1); }
    public ProfileImageData data() {
        return new ProfileImageData(id, imageUrl, originalFileName, "image/png", sizeBytes, activatedAt);
    }
}
