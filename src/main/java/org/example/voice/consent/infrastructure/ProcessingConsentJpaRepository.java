package org.example.voice.consent.infrastructure;

import org.example.voice.consent.domain.entity.ProcessingConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface ProcessingConsentJpaRepository extends JpaRepository<ProcessingConsent, Long> {

    @Modifying
    @Query("""
            update ProcessingConsent consent
               set consent.revokedAt = :revokedAt
             where consent.userId = :userId
               and consent.trainingSessionId = :sessionId
               and consent.revokedAt is null
            """)
    int revokeForSession(
            @Param("userId") Long userId,
            @Param("sessionId") Long sessionId,
            @Param("revokedAt") OffsetDateTime revokedAt
    );

    @Modifying
    @Query("""
            update ProcessingConsent consent
               set consent.revokedAt = :revokedAt
             where consent.userId = :userId
               and consent.revokedAt is null
            """)
    int revokeForUser(@Param("userId") Long userId, @Param("revokedAt") OffsetDateTime revokedAt);
}
