package org.example.voice.user.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.user.domain.type.UserRole;
import org.example.voice.user.domain.type.UserStatus;

import java.time.OffsetDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "nickname", nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "terms_agreed_at", nullable = false)
    private OffsetDateTime termsAgreedAt;

    @Column(name = "privacy_agreed_at", nullable = false)
    private OffsetDateTime privacyAgreedAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    private User(String email, String password, String nickname, OffsetDateTime now) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.status = UserStatus.ACTIVE;
        this.termsAgreedAt = now;
        this.privacyAgreedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
        this.role = UserRole.USER;
    }

    public static User createLocal(String email, String encodedPassword, String nickname, OffsetDateTime now) {
        return new User(email, encodedPassword, nickname, now);
    }

    public static User createSocial(String email, String nickname, OffsetDateTime now) {
        return new User(email, null, nickname, now);
    }

    public void recordLogin(OffsetDateTime now) {
        this.lastLoginAt = now;
        this.updatedAt = now;
    }

    public boolean isSuspended() {
        return status == UserStatus.SUSPENDED;
    }
}
