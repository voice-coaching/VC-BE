package org.example.voice.profileimage.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.profileimage.domain.ProfileImageException;
import org.example.voice.profileimage.domain.entity.ProfileImage;
import org.example.voice.profileimage.domain.model.ProfileImageData;
import org.example.voice.profileimage.domain.port.ProfileImageLifecycle;
import org.example.voice.profileimage.domain.port.ProfileImageRepository;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.port.UserReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileImageTransactions implements ProfileImageLifecycle {
    private final ProfileImageRepository images;
    private final UserReader users;
    private final Clock clock;
    public record Reservation(Long id, Long previousId, String objectKey, ProfileImageData replay) {}

    public ProfileImageData current(Long userId) {
        allowed(users.findById(userId).orElseThrow(() -> new ProfileImageException(404, "RESOURCE_NOT_FOUND")));
        return images.active(userId).map(ProfileImage::data).orElse(null);
    }

    @Transactional
    public Reservation reserve(Long userId, boolean replace, String keyDigest, String digest,
                               String objectKey, String imageUrl, String fileName, long size) {
        allowed(lock(userId));
        if (keyDigest != null) {
            var prior = images.byKey(userId, keyDigest);
            if (prior.isPresent()) {
                var image = prior.get();
                if (!image.getRequestDigest().equals(digest)) throw new ProfileImageException(409, "CONFLICT");
                if (image.getActivatedAt() == null) throw new ProfileImageException(409, "CONFLICT");
                return new Reservation(image.getId(), null, image.getObjectKey(), image.data());
            }
        }
        var current = images.active(userId);
        if (replace && current.isEmpty()) throw new ProfileImageException(404, "PROFILE_IMAGE_NOT_FOUND");
        if (!replace && current.isPresent()) throw new ProfileImageException(409, "PROFILE_IMAGE_EXISTS");
        var image = images.save(ProfileImage.reserve(userId, objectKey, imageUrl, fileName, size, keyDigest, digest, now()));
        return new Reservation(image.getId(), current.map(ProfileImage::getId).orElse(null), objectKey, null);
    }

    @Transactional
    public ProfileImageData activate(Long userId, Reservation reservation) {
        User user = lock(userId);
        allowed(user);
        var current = images.active(userId);
        if (!Objects.equals(current.map(ProfileImage::getId).orElse(null), reservation.previousId()))
            throw new ProfileImageException(409, "CONFLICT");
        var image = images.byId(reservation.id()).orElseThrow(() -> new ProfileImageException(409, "CONFLICT"));
        if (!image.getUserId().equals(userId)) throw new ProfileImageException(404, "RESOURCE_NOT_FOUND");
        current.ifPresent(old -> { old.retire(now()); images.save(old); });
        image.activate(now());
        images.save(image);
        user.updateProfileImageUrl(image.getImageUrl());
        return image.data();
    }

    @Transactional
    public void deleteCurrent(Long userId) {
        User user = lock(userId); allowed(user);
        images.owned(userId).stream().filter(i -> i.getState().equals("ACTIVE") || i.getState().equals("UPLOADING"))
                .forEach(i -> { i.retire(now()); images.save(i); });
        user.updateProfileImageUrl(null);
    }

    @Override @Transactional
    public void removeForUser(Long userId) {
        User user = lock(userId);
        images.owned(userId).stream().filter(i -> !i.getState().equals("DELETED"))
                .forEach(i -> { i.retire(now()); images.save(i); });
        user.updateProfileImageUrl(null);
    }

    public List<Long> cleanupDue() { return images.cleanupDue(now()).stream().map(ProfileImage::getId).toList(); }

    @Transactional
    public String claimCleanup(Long id) {
        var owner = images.owner(id);
        if (owner.isEmpty()) return null;
        lock(owner.get());
        var image = images.byId(id).orElseThrow();
        if (image.getState().equals("ACTIVE") || image.getState().equals("DELETED") || image.getCleanupAfter().isAfter(now())) return null;
        image.claimDeletion(now()); images.save(image);
        return image.getObjectKey();
    }

    @Transactional
    public void cleaned(Long id) {
        images.owner(id).ifPresent(owner -> {
            lock(owner);
            var image = images.byId(id).orElseThrow();
            if (image.getState().equals("DELETE_PENDING")) { image.deleted(); images.save(image); }
        });
    }

    private User lock(Long userId) {
        return users.findByIdForUpdate(userId).orElseThrow(() -> new ProfileImageException(404, "RESOURCE_NOT_FOUND"));
    }
    private void allowed(User user) {
        if (user.isWithdrawn() || user.isSuspended()) throw new ProfileImageException(403, "FORBIDDEN");
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
}
