package org.example.voice.profileimage.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.profileimage.domain.entity.ProfileImage;
import org.example.voice.profileimage.domain.port.ProfileImageRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

interface ProfileImageJpaRepository extends JpaRepository<ProfileImage, Long> {
    @Query("select i.userId from ProfileImage i where i.id = :id")
    Optional<Long> owner(Long id);
    Optional<ProfileImage> findByUserIdAndState(Long userId, String state);
    Optional<ProfileImage> findByUserIdAndKeyDigest(Long userId, String keyDigest);
    List<ProfileImage> findByUserId(Long userId);
    @Query("select i from ProfileImage i where i.state in ('UPLOADING','DELETE_PENDING') and i.cleanupAfter <= :now order by i.cleanupAfter, i.id")
    List<ProfileImage> cleanupDue(OffsetDateTime now, Pageable page);
}

@Repository
@RequiredArgsConstructor
public class ProfileImagePersistence implements ProfileImageRepository {
    private final ProfileImageJpaRepository repository;
    public Optional<ProfileImage> active(Long userId) { return repository.findByUserIdAndState(userId, "ACTIVE"); }
    public Optional<ProfileImage> byKey(Long userId, String digest) { return repository.findByUserIdAndKeyDigest(userId, digest); }
    public Optional<ProfileImage> byId(Long id) { return repository.findById(id); }
    public Optional<Long> owner(Long id) { return repository.owner(id); }
    public ProfileImage save(ProfileImage image) { return repository.saveAndFlush(image); }
    public List<ProfileImage> owned(Long userId) { return repository.findByUserId(userId); }
    public List<ProfileImage> cleanupDue(OffsetDateTime now) { return repository.cleanupDue(now, Pageable.ofSize(100)); }
}
