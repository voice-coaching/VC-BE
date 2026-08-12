package org.example.voice.auth.infrastructure;

import jakarta.persistence.LockModeType;
import org.example.voice.auth.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.userId = :userId")
    Optional<RefreshToken> findByUserIdForUpdate(@Param("userId") Long userId);

    void deleteByUserId(Long userId);
    boolean existsByUserIdAndSessionIdAndExpiresAtAfter(Long userId, String sessionId, java.time.OffsetDateTime now);
}
