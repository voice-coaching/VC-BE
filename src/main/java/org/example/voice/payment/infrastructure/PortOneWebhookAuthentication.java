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
        if (!settings.enabled() || settings.webhookSecret().isBlank()) throw new PaymentException(503, "WEBHOOK_NOT_CONFIGURED");
        if (id == null || id.isBlank() || id.length() > 200) throw new PaymentException(401, "WEBHOOK_SIGNATURE_INVALID");
        try {
            var event = new WebhookVerifier(settings.webhookSecret()).verify(body, id, signature, timestamp);
            return event instanceof WebhookTransaction transaction ? transaction.getData().getPaymentId() : null;
        } catch (Exception error) { throw new PaymentException(401, "WEBHOOK_SIGNATURE_INVALID"); }
    }
}
