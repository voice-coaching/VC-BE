package org.example.voice.auth.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.auth.domain.type.OAuthProvider;

import java.time.OffsetDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "social_accounts")
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = true)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = true)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = true)
    private String providerUserId;

    @Column(name = "provider_email")
    private String providerEmail;

    @Column(name = "created_at", nullable = true)
    private OffsetDateTime createdAt;

    private SocialAccount(Long userId, OAuthProvider provider, String providerUserId, String providerEmail, OffsetDateTime now) {
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.providerEmail = providerEmail;
        this.createdAt = now;
    }

    public static SocialAccount create(Long userId, OAuthProvider provider, String providerUserId, String providerEmail, OffsetDateTime now) {
        return new SocialAccount(userId, provider, providerUserId, providerEmail, now);
    }
}
