package org.example.voice.payment.domain;

import java.time.OffsetDateTime;

public record Membership(String type, OffsetDateTime expiresAt, String environment) {
    public static final String FREE = "free";
    public static final String PAID = "paid";
    public static final String TEST_ADMIN = "admin";
    public static final String TEST_ENVIRONMENT = "TEST";

    public static Membership free() { return new Membership(FREE, null, null); }
    public static Membership testAdmin() { return new Membership(TEST_ADMIN, null, TEST_ENVIRONMENT); }
    public static Membership testPaid(OffsetDateTime expiresAt) {
        return new Membership(PAID, expiresAt, TEST_ENVIRONMENT);
    }
}
