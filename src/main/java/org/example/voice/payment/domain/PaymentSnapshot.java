package org.example.voice.payment.domain;

import java.time.OffsetDateTime;

/** Only the server-to-server fields needed to verify an order; no customer/card data. */
public record PaymentSnapshot(String id, String storeId, String channelKey, String channelType,
                              String status, long total, long cancelled, String currency,
                              OffsetDateTime paidAt, OffsetDateTime updatedAt) {}
