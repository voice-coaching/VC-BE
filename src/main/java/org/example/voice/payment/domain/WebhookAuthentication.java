package org.example.voice.payment.domain;

public interface WebhookAuthentication {
    /** Returns a payment id for transaction events, or null for other authenticated events. */
    String verify(String body, String id, String signature, String timestamp);
}
