package org.example.voice.payment.domain.model;

import java.time.OffsetDateTime;

public record PremiumTrial(long userId, OffsetDateTime startsAt, OffsetDateTime expiresAt) {
    public boolean active(OffsetDateTime now) {
        return !now.isBefore(startsAt) && now.isBefore(expiresAt);
    }
}
