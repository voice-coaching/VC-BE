package org.example.voice.profileimage.domain.port;

import org.example.voice.profileimage.domain.entity.ProfileImage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ProfileImageRepository {
    Optional<ProfileImage> active(Long userId);
    Optional<ProfileImage> byKey(Long userId, String digest);
    Optional<ProfileImage> byId(Long id);
    Optional<Long> owner(Long id);
    ProfileImage save(ProfileImage image);
    List<ProfileImage> owned(Long userId);
    List<ProfileImage> cleanupDue(OffsetDateTime now);
}
