package org.example.voice.consent.domain.model;

import java.time.OffsetDateTime;

public record ProcessingConsentReceipt(
        String receiptSha256,
        OffsetDateTime grantedAt
) {
}
