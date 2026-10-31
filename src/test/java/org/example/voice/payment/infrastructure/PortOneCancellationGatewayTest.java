package org.example.voice.payment.infrastructure;

import com.sun.net.httpserver.HttpServer;
import org.example.voice.payment.domain.PaymentException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortOneCancellationGatewayTest {
    private HttpServer server;
    private PortOnePaymentGateway gateway;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicInteger posts = new AtomicInteger();
    private final String id = "test00000000000040008000000000000001";
    private final String requestId = UUID.randomUUID().toString();
    private String channelType = "TEST";
    private String status = "PAID";
    private long cancelled;
    private int postCode = 200;
    private String postBody = "{\"cancellation\":{\"status\":\"SUCCEEDED\"}}";

    @BeforeEach void start() throws Exception {
        var settings = new TestPaymentSettings(true, "store", "channel", "fixture-secret", "fixture-webhook-secret");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/payments/", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("PortOne fixture-secret");
            boolean post = exchange.getRequestMethod().equals("POST");
            if (post) {
                posts.incrementAndGet();
                assertThat(exchange.getRequestURI().getPath()).isEqualTo("/payments/" + id + "/cancel");
                assertThat(exchange.getRequestHeaders().getFirst("Idempotency-Key")).isEqualTo("\"" + requestId + "\"");
                var body = json.readTree(exchange.getRequestBody().readAllBytes());
                assertThat(body.path("amount").longValue()).isEqualTo(15000);
                assertThat(body.path("currentCancellableAmount").longValue()).isEqualTo(15000);
                assertThat(body.path("storeId").asText()).isEqualTo("store");
                assertThat(body.path("requester").asText()).isEqualTo("CUSTOMER");
                assertThat(body.path("reason").asText()).isEqualTo("customer request");
            }
            String body = post ? postBody : "{\"id\":\"" + id + "\",\"storeId\":\"store\","
                    + "\"channel\":{\"key\":\"channel\",\"type\":\"" + channelType + "\"},"
                    + "\"status\":\"" + status + "\",\"amount\":{\"total\":15000,\"cancelled\":" + cancelled + "},"
                    + "\"currency\":\"KRW\",\"paidAt\":\"2026-10-28T00:00:00Z\",\"updatedAt\":\"2026-10-28T00:00:00Z\"}";
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(post ? postCode : 200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        gateway = new PortOnePaymentGateway(settings, json,
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void fixedFullAmountAndStableQuotedKeyAreSentToOfficialCancelPath() {
        assertThat(gateway.cancel(id, requestId, "customer request", 15000)).isEqualTo("SUCCEEDED");
        assertThat(posts.get()).isEqualTo(1);
    }

    @Test void livePartialAndAlreadyCancelledPaymentsNeverSendCancellation() {
        channelType = "LIVE";
        assertThatThrownBy(() -> gateway.cancel(id, requestId, "customer request", 15000))
                .hasMessage("PAYMENT_CHANNEL_MISMATCH");
        channelType = "TEST";
        cancelled = 100;
        assertThatThrownBy(() -> gateway.cancel(id, requestId, "customer request", 15000))
                .hasMessage("PAYMENT_NOT_CANCELLABLE");
        status = "CANCELLED";
        cancelled = 15000;
        assertThat(gateway.cancel(id, requestId, "customer request", 15000)).isEqualTo("SUCCEEDED");
        assertThat(posts.get()).isZero();
    }

    @Test void invalidAmountsResponsesAndProviderFailuresDoNotLeakPayloads() {
        assertThatThrownBy(() -> gateway.cancel(id, requestId, "customer request", 1))
                .hasMessage("INVALID_CANCELLATION_REQUEST");
        postBody = "{\"cancellation\":{\"status\":\"unexpected\"}}";
        assertThatThrownBy(() -> gateway.cancel(id, requestId, "customer request", 15000))
                .hasMessage("PAYMENT_PROVIDER_INVALID_RESPONSE");
        postCode = 500;
        postBody = "private-card-data";
        assertThatThrownBy(() -> gateway.cancel(id, requestId, "customer request", 15000))
                .isInstanceOf(PaymentException.class).hasMessage("PAYMENT_PROVIDER_UNAVAILABLE").hasNoCause();
    }
}
