package org.example.voice.payment.infrastructure;

import io.portone.sdk.server.webhook.WebhookTransaction;
import io.portone.sdk.server.webhook.WebhookVerifier;
import org.example.voice.payment.domain.*;
import org.springframework.stereotype.Component;

@Component
public class PortOneWebhookAuthentication implements WebhookAuthentication {
    private final TestPaymentSettings settings;
    public PortOneWebhookAuthentication(TestPaymentSettings settings) { this.settings = settings; }
    @Override public String verify(String body, String id, String signature, String timestamp) {
        try { return new tools.jackson.databind.ObjectMapper().readTree(body).path("data").path("paymentId").asText(null); }
        catch (Exception error) { throw new PaymentException(400, "INVALID_JSON"); }
    }
}
