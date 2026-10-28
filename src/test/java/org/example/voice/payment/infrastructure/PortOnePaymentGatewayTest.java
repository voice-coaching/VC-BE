package org.example.voice.payment.infrastructure;

import com.sun.net.httpserver.HttpServer;
import org.example.voice.payment.domain.PaymentException;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class PortOnePaymentGatewayTest {
    private HttpServer server;
    private PortOnePaymentGateway gateway;
    private final AtomicInteger code = new AtomicInteger(200);
    private String body;
    private final String id = "test_00000000-0000-4000-8000-000000000001";
    @BeforeEach void start() throws Exception {
        var env = new MockEnvironment(); env.setActiveProfiles("payment-test");
        var settings = new TestPaymentSettings(true, "1", "store", "channel", "test-only-secret", "", env);
        body = "{\"id\":\"" + id + "\",\"storeId\":\"store\",\"channel\":{\"key\":\"channel\",\"type\":\"TEST\"},\"status\":\"PAID\",\"amount\":{\"total\":15000,\"cancelled\":0},\"currency\":\"KRW\",\"paidAt\":\"2026-10-28T00:00:00Z\",\"updatedAt\":\"2026-10-28T00:00:00Z\"}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/payments/", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("PortOne test-only-secret");
            assertThat(exchange.getRequestURI().getQuery()).isEqualTo("storeId=store");
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:1/never-follow");
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code.get(), bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); } catch (java.io.IOException ignored) {}
        });
        server.start();
        gateway = new PortOnePaymentGateway(settings, new ObjectMapper(), HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void readsOnlyNecessaryProviderFields() {
        var snapshot = gateway.get(id);
        assertThat(snapshot.total()).isEqualTo(15000);
        assertThat(snapshot.channelType()).isEqualTo("TEST");
        assertThat(snapshot.status()).isEqualTo("PAID");
    }
    @Test void providerErrorsAndRedirectsNeverReturnRawBodies() {
        body = "private-provider-data";
        for (var status : new int[]{302, 401, 429, 500}) {
            code.set(status);
            assertThatThrownBy(() -> gateway.get(id)).isInstanceOf(PaymentException.class)
                    .hasMessage("PAYMENT_PROVIDER_UNAVAILABLE");
        }
        code.set(404);
        assertThatThrownBy(() -> gateway.get(id)).hasMessage("PAYMENT_NOT_COMPLETED");
    }
    @Test void oversizedMalformedAndNonIntegerAmountFailClosed() {
        body = "x".repeat(1024 * 1024 + 1);
        assertThatThrownBy(() -> gateway.get(id)).isInstanceOf(PaymentException.class);
        body = "{invalid}";
        assertThatThrownBy(() -> gateway.get(id)).isInstanceOf(PaymentException.class);
        body = "{\"amount\":{\"total\":15000.1,\"cancelled\":0}}";
        assertThatThrownBy(() -> gateway.get(id)).hasMessage("PAYMENT_PROVIDER_INVALID_RESPONSE");
    }
    @Test void invalidAuthorizationConfigurationDoesNotLeakSecretThroughExceptions() {
        var env = new MockEnvironment(); env.setActiveProfiles("payment-test");
        var settings = new TestPaymentSettings(true, "1", "store", "channel", "private-invalid\nvalue", "", env);
        var invalid = new PortOnePaymentGateway(settings, new ObjectMapper());
        assertThatThrownBy(() -> invalid.get(id)).isInstanceOf(PaymentException.class)
                .hasMessage("PAYMENT_PROVIDER_UNAVAILABLE").hasNoCause();
    }
}
