package org.example.voice.notification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.example.voice.notification.infrastructure.NativeFcmDispatcher;
import org.example.voice.notification.infrastructure.NativeFcmTokenCipher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Isolated PostgreSQL only: exercises real Flyway, authentication, registration and trigger. */
@EnabledIfEnvironmentVariable(named = "VC_BE_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "management.server.port=0", "management.server.address=127.0.0.1",
        "auth.session-cache.enabled=false", "analysis.transport=disabled", "analysis.stream.enabled=false",
        "notification.native-fcm.enabled=false",
        "notification.native-fcm.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "custom-content.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
class NativeFcmPostgresHttpTest {
    private static final String SCHEMA = "native_fcm_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        String url = System.getenv("VC_BE_TEST_POSTGRES_URL");
        if (!url.startsWith("jdbc:postgresql://127.0.0.1:")) throw new IllegalStateException("Requires isolated loopback PostgreSQL");
        String user = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres");
        String password = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", "");
        try (var c = DriverManager.getConnection(url, user, password); var s = c.createStatement()) { s.execute("CREATE SCHEMA " + SCHEMA); }
        registry.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
    }

    @AfterAll static void cleanSchema() throws Exception {
        String url = System.getenv("VC_BE_TEST_POSTGRES_URL");
        try (var c = DriverManager.getConnection(url, System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres"),
                System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", "")); var s = c.createStatement()) {
            s.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }

    @Autowired Environment env;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test void registrationIsAuthenticatedOwnerScopedAndDrivesOutbox() throws Exception {
        JsonNode first = signup(); JsonNode second = signup();
        String firstToken = first.path("accessToken").asText();
        String secondToken = second.path("accessToken").asText();
        long firstUser = first.path("userId").asLong();
        UUID installation = UUID.randomUUID();
        String deviceToken = "synthetic-device-token-" + UUID.randomUUID();
        Map<String, String> body = Map.of("installationId", installation.toString(), "token", deviceToken);
        assertThat(call("PUT", "/api/users/me/native-fcm-devices", body, null).statusCode()).isEqualTo(401);
        assertThat(call("PUT", "/api/users/me/native-fcm-devices", body, firstToken).statusCode()).isEqualTo(200);
        String encrypted = jdbc.queryForObject("SELECT encrypted_token FROM native_fcm_devices WHERE user_id=?", String.class, firstUser);
        assertThat(encrypted).startsWith("v1:").doesNotContain(deviceToken);
        assertThat(call("PUT", "/api/users/me/native-fcm-devices", body, firstToken).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT encrypted_token FROM native_fcm_devices WHERE user_id=?", String.class, firstUser)).isEqualTo(encrypted);
        assertThat(call("PUT", "/api/users/me/native-fcm-devices", body, secondToken).statusCode()).isEqualTo(409);
        String rotatedToken = "rotated-synthetic-token-" + UUID.randomUUID();
        assertThat(call("PUT", "/api/users/me/native-fcm-devices", Map.of("installationId", installation.toString(), "token", rotatedToken), firstToken).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT encrypted_token FROM native_fcm_devices WHERE user_id=?", String.class, firstUser)).isNotEqualTo(encrypted).doesNotContain(rotatedToken);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM native_fcm_devices", Long.class)).isEqualTo(1L);
        jdbc.update("INSERT INTO user_notifications(user_id,type,title,body,deep_link,deduplication_key,created_at) VALUES (?,'TEST','test','test','/home',?,now())", firstUser, UUID.randomUUID().toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM native_fcm_outbox", Long.class)).isEqualTo(1L);
        FirebaseMessaging sender = mock(FirebaseMessaging.class);
        when(sender.send(any(Message.class))).thenReturn("synthetic-message-id");
        var dispatcher = new NativeFcmDispatcher(jdbc, new TransactionTemplate(transactions),
                new NativeFcmTokenCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="), "test-project", Duration.ofSeconds(15)) {
            @Override protected FirebaseMessaging messaging() { return sender; }
        };
        dispatcher.dispatch();
        verify(sender).send(any(Message.class));
        assertThat(jdbc.queryForObject("SELECT state FROM native_fcm_outbox", String.class)).isEqualTo("SENT");
        assertThat(call("DELETE", "/api/users/me/native-fcm-devices/" + installation, null, secondToken).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM native_fcm_devices", Long.class)).isEqualTo(1L);
        assertThat(call("DELETE", "/api/users/me/native-fcm-devices/" + installation, null, firstToken).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM native_fcm_outbox", Long.class)).isZero();
    }

    private JsonNode signup() throws Exception {
        var response = call("POST", "/api/auth/signup", Map.of("email", UUID.randomUUID() + "@example.invalid", "password", "Synthetic-fixture-123!", "nickname", "fixture", "termsAgreed", true, "privacyAgreed", true), null);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).path("data");
    }

    private HttpResponse<String> call(String method, String path, Object body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + env.getProperty("local.server.port") + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
