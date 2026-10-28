package org.example.voice.payment.infrastructure;

import org.example.voice.payment.domain.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;

@Component
public class PortOnePaymentGateway implements PaymentGateway {
    private static final int MAX_BODY = 1024 * 1024;
    private final TestPaymentSettings settings;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final URI base;
    @org.springframework.beans.factory.annotation.Autowired
    public PortOnePaymentGateway(TestPaymentSettings settings, ObjectMapper mapper) {
        this(settings, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build(), URI.create("https://api.portone.io/"));
    }
    // Package-private seam for loopback HTTP contract tests; the production endpoint is fixed.
    PortOnePaymentGateway(TestPaymentSettings settings, ObjectMapper mapper, HttpClient client, URI base) {
        this.settings = settings; this.mapper = mapper; this.client = client; this.base = base;
    }
    @Override public PaymentSnapshot get(String paymentId) {
        if (!settings.enabled()) throw new PaymentException(403, "TEST_PAYMENT_DISABLED");
        if (!paymentId.matches("test[a-f0-9]{32}")) throw new PaymentException(400, "INVALID_PAYMENT_ID");
        var uri = base.resolve("payments/" + paymentId + "?storeId="
                + URLEncoder.encode(settings.checkout().storeId(), StandardCharsets.UTF_8));
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                    .header("Authorization", "PortOne " + settings.apiSecret()).header("Accept", "application/json").GET().build();
            pending = client.sendAsync(request, ignored -> new LimitedBody());
            var response = pending.get(10, TimeUnit.SECONDS);
            if (response.statusCode() == 404) throw new PaymentException(409, "PAYMENT_NOT_COMPLETED");
            if (response.statusCode() != 200) throw new PaymentException(502, "PAYMENT_PROVIDER_UNAVAILABLE");
            return parse(mapper.readTree(response.body()));
        } catch (PaymentException error) { throw error; }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new PaymentException(503, "PAYMENT_PROVIDER_UNAVAILABLE");
        } catch (Exception error) {
            // Never log the provider body, authorization header, URL or customer data.
            throw new PaymentException(502, "PAYMENT_PROVIDER_UNAVAILABLE");
        } finally { if (pending != null) pending.cancel(true); }
    }
    private PaymentSnapshot parse(JsonNode json) {
        try {
            var amount = json.path("amount"); var channel = json.path("channel");
            if (!amount.path("total").isIntegralNumber() || !amount.path("cancelled").isIntegralNumber()
                    || !amount.path("total").canConvertToLong() || !amount.path("cancelled").canConvertToLong()
                    || amount.path("total").longValue() < 0 || amount.path("cancelled").longValue() < 0)
                throw new IllegalArgumentException();
            return new PaymentSnapshot(required(json, "id"), required(json, "storeId"), required(channel, "key"),
                    required(channel, "type"), required(json, "status"), amount.path("total").longValue(),
                    amount.path("cancelled").longValue(), required(json, "currency"),
                    json.path("paidAt").isTextual() ? OffsetDateTime.parse(json.path("paidAt").asText()) : null,
                    OffsetDateTime.parse(required(json, "updatedAt")));
        } catch (RuntimeException error) { throw new PaymentException(502, "PAYMENT_PROVIDER_INVALID_RESPONSE"); }
    }
    private String required(JsonNode node, String key) {
        if (!node.path(key).isTextual() || node.path(key).asText().isBlank()) throw new IllegalArgumentException();
        return node.path(key).asText();
    }
    private static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(Long.MAX_VALUE); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if ((long) bytes.size() + buffer.remaining() > MAX_BODY) {
                    subscription.cancel(); body.completeExceptionally(new IllegalArgumentException("Response exceeds size limit")); return;
                }
                var chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
        }
        @Override public void onError(Throwable error) { body.completeExceptionally(error); }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
