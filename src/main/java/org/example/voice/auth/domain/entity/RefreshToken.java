package org.example.voice.auth.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Getter
@Table(name = "refresh_tokens", uniqueConstraints = @UniqueConstraint(name = "uk_refresh_tokens_user", columnNames = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "session_id", nullable = false, unique = true, length = 36)
    private String sessionId;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private long version;

    private RefreshToken(Long userId, String tokenHash, String sessionId, OffsetDateTime expiresAt, OffsetDateTime now) {
        this.userId = userId;
        rotate(tokenHash, sessionId, expiresAt, now);
    }

    public static RefreshToken issue(Long userId, String tokenHash, String sessionId, OffsetDateTime expiresAt, OffsetDateTime now) {
        return new RefreshToken(userId, tokenHash, sessionId, expiresAt, now);
    }

    public void rotate(String tokenHash, String sessionId, OffsetDateTime expiresAt, OffsetDateTime now) {
        this.tokenHash = tokenHash;
        this.sessionId = sessionId;
        this.expiresAt = expiresAt;
        this.updatedAt = now;
    }

    public boolean isExpired(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }
}
