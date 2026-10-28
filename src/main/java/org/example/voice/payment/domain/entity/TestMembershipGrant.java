package org.example.voice.payment.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_test_membership_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestMembershipGrant {
    @Id
    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private OffsetDateTime grantedAt;

    public static TestMembershipGrant create(Long userId, OffsetDateTime grantedAt) {
        var grant = new TestMembershipGrant();
        grant.userId = userId;
        grant.grantedAt = grantedAt;
        return grant;
    }
}
