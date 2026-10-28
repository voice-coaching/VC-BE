package org.example.voice.payment.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.payment.domain.*;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentTransactions transactions;
    private final PaymentGateway gateway;
    private final WebhookAuthentication webhook;

    public PaymentOrder verify(Long userId, String orderId) {
        var order = transactions.owned(userId, orderId);
        var snapshot = gateway.get(order.getId());
        return transactions.apply(userId, orderId, snapshot, null, null);
    }
    public void webhook(String body, String eventId, String signature, String timestamp) {
        var paymentId = webhook.verify(body, eventId, signature, timestamp);
        if (paymentId == null) return;
        var order = transactions.findForWebhook(paymentId);
        if (order == null) return; // This endpoint does not adopt unrelated payments.
        var snapshot = gateway.get(paymentId);
        transactions.apply(order.getUserId(), paymentId, snapshot, eventId, digest(body));
    }
    private String digest(String body) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
