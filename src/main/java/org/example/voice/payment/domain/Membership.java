package org.example.voice.payment.domain;

import java.time.OffsetDateTime;

public record Membership(String type, OffsetDateTime expiresAt, String environment) {
    public static Membership free() { return new Membership("free", null, null); }
}
