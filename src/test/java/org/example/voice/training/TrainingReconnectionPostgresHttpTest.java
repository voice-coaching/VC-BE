package org.example.voice.training;

import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;
import org.example.voice.analysis.domain.type.AnalysisExecutionProfile;
import org.example.voice.training.application.TrainingAnalysisSchemaAdmissionService;
import org.example.voice.training.domain.model.NormalizedRecordingData;
import org.example.voice.training.domain.port.*;
import org.example.voice.training.domain.type.RecordingQualityStatus;
import org.example.voice.training.infrastructure.RecordingUploadIntentSweeper;
import org.example.voice.common.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real HTTP/security/JPA/Flyway/PG. Only external storage/media/AI admission are substitutes. */
@EnabledIfEnvironmentVariable(named = "VC_BE_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "auth.session-cache.enabled=false", "analysis.transport=disabled", "analysis.stream.enabled=false",
        "server.address=127.0.0.1", "management.server.port=0", "management.server.address=127.0.0.1",
        "storage.upload-intent-sweep-interval-ms=3600000", "custom-content.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
class TrainingReconnectionPostgresHttpTest {
    private static final String SCHEMA = "reconnect_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) throws Exception {
        String url = System.getenv("VC_BE_TEST_POSTGRES_URL");
        if (!url.startsWith("jdbc:postgresql://127.0.0.1:")) throw new IllegalStateException("Requires isolated loopback PostgreSQL");
        String user = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres");
        String password = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", "");
        try (var c = DriverManager.getConnection(url, user, password); var statement = c.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
        r.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        r.add("spring.datasource.username", () -> user); r.add("spring.datasource.password", () -> password);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> true); r.add("spring.flyway.schemas", () -> SCHEMA);
        r.add("spring.flyway.default-schema", () -> SCHEMA);
        r.add("spring.datasource.hikari.maximum-pool-size", () -> 8);
    }

    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired RecordingUploadIntentSweeper sweeper;
    @MockitoBean RecordingObjectStoragePort storage;
    @MockitoBean RecordingMediaNormalizationPort normalizer;
    @MockitoBean AnalysisJobPublisher publisher;
    @MockitoBean TrainingAnalysisSchemaAdmissionService admission;
    @MockitoBean CacheManager caches;
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    String token, base;
    Long userId, contentId;

    @BeforeEach void fixtures() throws Exception {
        base = "http://127.0.0.1:" + environment.getProperty("local.server.port");
        var localCaches = new ConcurrentMapCacheManager();
        when(caches.getCache(anyString())).thenAnswer(c -> localCaches.getCache(c.getArgument(0)));
        when(admission.selectProfile(nullable(String.class))).thenReturn(AnalysisExecutionProfile.CANONICAL_AUDIOVISUAL);
        when(storage.createObjectKey(anyLong(), anyLong(), anyString())).thenAnswer(c ->
                "recordings/users/" + c.getArgument(0) + "/sessions/" + c.getArgument(1) + "/" + UUID.randomUUID() + ".wav");
        when(storage.createUploadUrl(anyString(), anyString(), anyLong(), any())).thenReturn("https://storage.example.invalid/fixture");
        when(storage.requiredHeaders(anyString(), anyLong())).thenReturn(Map.of("Content-Type", "audio/wav"));
        when(normalizer.normalize(anyLong(), anyLong(), anyString(), anyString(), anyLong(), nullable(org.example.voice.training.domain.model.VisualProcessingAuthorizationData.class)))
                .thenAnswer(c -> new NormalizedRecordingData(c.<String>getArgument(2) + ".normalized.wav", "audio/wav", 32044L,
                        1000, "a".repeat(64), RecordingQualityStatus.PASS, BigDecimal.ONE, BigDecimal.ZERO));
        doAnswer(c -> {
            AnalysisWorkerRequest request = c.getArgument(0);
            jdbc.update("INSERT INTO analysis_request_outbox(event_id,analysis_id,payload,status,transport) VALUES(?,?,'{}','PENDING','REDIS_STREAM')",
                    request.eventId().toString(), request.analysisId());
            return null;
        }).when(publisher).publish(any(), any());
        token = null;
        var account = call("POST", "/api/auth/signup", Map.of("email", UUID.randomUUID() + "@example.invalid", "password", "Reconnect-fixture-123!",
                "nickname", "reconnect fixture", "termsAgreed", true, "privacyAgreed", true), null, 200);
        token = account.path("data").path("accessToken").asText();
        userId = account.path("data").path("userId").asLong();
        contentId = jdbc.queryForObject("SELECT id FROM practice_contents WHERE owner_id IS NULL ORDER BY id LIMIT 1", Long.class);
    }

    @Test void lostSessionCreationAcknowledgementAndConcurrentReplayCreateOneSession() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = json.writeValueAsString(createBody());
        loseAcknowledgement("/api/training-sessions", body, key, () -> countReceipts("CREATE_SESSION") == 1);
        var replies = parallel(6, () -> call("POST", "/api/training-sessions", createBody(), key, 200));
        long id = replies.getFirst().path("data").path("sessionId").asLong();
        assertThat(replies).allSatisfy(r -> assertThat(r.path("data").path("sessionId").asLong()).isEqualTo(id));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM training_sessions WHERE user_id=?", Long.class, userId)).isEqualTo(1);
        assertThat(countReceipts("CREATE_SESSION")).isEqualTo(1);
    }

    @Test void requestKeysAreOwnerScopedAndDifferentBodiesAreRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        var first = call("POST", "/api/training-sessions", createBody(), key, 200);
        var different = new HashMap<>(createBody()); different.put("sentenceIndex", 0); different.put("sentenceText", "Different fixture sentence.");
        assertCode(call("POST", "/api/training-sessions", different, key, 409), "IDEMPOTENCY_KEY_CONFLICT");
        assertCode(call("POST", "/api/training-sessions", createBody(), "invalid key", 400), "INVALID_IDEMPOTENCY_KEY");
        String other = otherAccount(); String original = token; token = other;
        try {
            var second = call("POST", "/api/training-sessions", createBody(), key, 200);
            assertThat(second.path("data").path("sessionId").asLong()).isNotEqualTo(first.path("data").path("sessionId").asLong());
            call("GET", "/api/training-sessions/" + first.path("data").path("sessionId").asLong() + "/recovery", null, null, 404);
        } finally { token = original; }
    }

    @Test void uploadResponseLossReusesObjectAndRecoveryDistinguishesMissingUploadedAndRegistered() throws Exception {
        long session = session(); String path = "/api/training-sessions/" + session; String key = UUID.randomUUID().toString();
        loseAcknowledgement(path + "/recordings/upload-url", json.writeValueAsString(uploadBody()), key, () -> countReceipts("ISSUE_UPLOAD") == 1);
        var issued = call("POST", path + "/recordings/upload-url", uploadBody(), key, 200).path("data");
        var again = call("POST", path + "/recordings/upload-url", uploadBody(), key, 200).path("data");
        assertThat(again.path("objectKey").asText()).isEqualTo(issued.path("objectKey").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recording_upload_intents WHERE training_session_id=?", Long.class, session)).isEqualTo(1);
        assertThat(recovery(session).path("uploads").get(0).path("status").asText()).isEqualTo("UPLOAD_REQUIRED");
        when(storage.isUploadedObject(eq(userId), eq(session), anyString(), eq("audio/wav"), eq(32044L))).thenReturn(true);
        assertThat(recovery(session).path("uploads").get(0).path("status").asText()).isEqualTo("READY_TO_REGISTER");
        var request = registration(issued.path("objectKey").asText());
        var registered = call("POST", path + "/recordings", request, null, 200).path("data");
        // Natural object identity also handles registration ACK loss for existing clients without a header.
        var replayed = call("POST", path + "/recordings", request, null, 200).path("data");
        assertThat(replayed.path("recordingId").asLong()).isEqualTo(registered.path("recordingId").asLong());
        assertThat(recovery(session).path("uploads").get(0).path("status").asText()).isEqualTo("REGISTERED");
        jdbc.update("UPDATE recording_upload_intents SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 hour' WHERE training_session_id=?", session);
        sweeper.sweep();
        assertThat(jdbc.queryForObject("SELECT status FROM recording_upload_intents WHERE training_session_id=?", String.class, session)).isEqualTo("CONSUMED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM voice_recordings WHERE training_session_id=?", Long.class, session)).isEqualTo(1);
        verify(normalizer, times(1)).normalize(anyLong(), anyLong(), anyString(), anyString(), anyLong(), nullable(org.example.voice.training.domain.model.VisualProcessingAuthorizationData.class));
        // Older consumed rows may not have a backfilled recording link; do not invent one or return 500.
        jdbc.update("UPDATE recording_upload_intents SET recording_id=NULL WHERE training_session_id=?", session);
        assertThat(recovery(session).path("uploads").get(0).path("status").asText()).isEqualTo("RECORDING_UNAVAILABLE");
    }

    @Test void missingObjectAndStorageFailureNeverRegisterOrCreateAReceipt() throws Exception {
        long session = session(); String path = "/api/training-sessions/" + session;
        String object = call("POST", path + "/recordings/upload-url", uploadBody(), "upload", 200).path("data").path("objectKey").asText();
        doThrow(new BaseException(ErrorCode.UPLOADED_OBJECT_NOT_FOUND)).when(storage).assertUploadedObject(anyLong(), anyLong(), anyString(), anyString(), anyLong());
        call("POST", path + "/recordings", registration(object), "register", 404);
        assertThat(countReceipts("REGISTER_RECORDING")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM voice_recordings WHERE training_session_id=?", Long.class, session)).isZero();
        when(storage.isUploadedObject(anyLong(), anyLong(), anyString(), anyString(), anyLong()))
                .thenThrow(new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE));
        assertCode(call("GET", path + "/recovery", null, null, 503), "ANALYSIS_INTEGRATION_UNAVAILABLE");
        verifyNoInteractions(normalizer);
    }

    @Test void expiredUploadCannotBeRenewedOrRegisteredUsingTheOldKey() throws Exception {
        long session = session(); String path = "/api/training-sessions/" + session;
        String object = call("POST", path + "/recordings/upload-url", uploadBody(), "upload", 200).path("data").path("objectKey").asText();
        jdbc.update("UPDATE recording_upload_intents SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 hour' WHERE training_session_id=?", session);
        assertThat(recovery(session).path("uploads").get(0).path("status").asText()).isEqualTo("EXPIRED");
        assertCode(call("POST", path + "/recordings/upload-url", uploadBody(), "upload", 409), "UPLOAD_INTENT_NOT_ACTIVE");
        assertCode(call("POST", path + "/recordings", registration(object), "register", 409), "UPLOAD_INTENT_NOT_ACTIVE");
        String fresh = call("POST", path + "/recordings/upload-url", uploadBody(), "new-upload", 200).path("data").path("objectKey").asText();
        assertThat(fresh).isNotEqualTo(object);
    }

    @Test void lostAnalysisResponseAndConcurrentReplayCreateOneAnalysisAndOneOutboxEvenIfAdmissionCloses() throws Exception {
        long session = recordedSession(); String path = "/api/training-sessions/" + session; String key = UUID.randomUUID().toString();
        loseAcknowledgement(path + "/analyze", "{}", key, () -> countReceipts("REQUEST_ANALYSIS") == 1);
        doThrow(new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE)).when(admission).assertAvailable(nullable(String.class));
        var replies = parallel(5, () -> call("POST", path + "/analyze", Map.of(), key, 200));
        long analysis = replies.getFirst().path("data").path("analysisId").asLong();
        assertThat(replies).allSatisfy(r -> assertThat(r.path("data").path("analysisId").asLong()).isEqualTo(analysis));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analysis_results a JOIN voice_recordings r ON r.id=a.recording_id WHERE r.training_session_id=?", Long.class, session)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analysis_request_outbox WHERE analysis_id=?", Long.class, analysis)).isEqualTo(1);
        assertThat(recovery(session).path("analysis").path("analysisId").asLong()).isEqualTo(analysis);
        for (String status : List.of("PROCESSING", "COMPLETED", "FAILED")) {
            jdbc.update("UPDATE analysis_results SET status=? WHERE id=?", status, analysis);
            assertThat(recovery(session).path("analysis").path("status").asText()).isEqualTo(status);
        }
    }

    @Test void failedAdmissionRollsBackBeforeRetryAndChangedSelectedRecordingCannotReuseTheKey() throws Exception {
        long session = recordedSession(); String path = "/api/training-sessions/" + session;
        doThrow(new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE)).when(publisher).publish(any(), any());
        call("POST", path + "/analyze", Map.of(), "analysis", 503);
        assertThat(countReceipts("REQUEST_ANALYSIS")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analysis_results a JOIN voice_recordings r ON r.id=a.recording_id WHERE r.training_session_id=?", Long.class, session)).isZero();
        doNothing().when(publisher).publish(any(), any());
        call("POST", path + "/analyze", Map.of(), "analysis", 200);
        jdbc.update("UPDATE voice_recordings SET is_selected=false WHERE training_session_id=?", session);
        assertCode(call("POST", path + "/analyze", Map.of(), "analysis", 409), "IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test void concurrentRegistrationAndRetryAreIdempotent() throws Exception {
        long session = session(); String path = "/api/training-sessions/" + session;
        String object = call("POST", path + "/recordings/upload-url", uploadBody(), "upload", 200).path("data").path("objectKey").asText();
        var registered = parallel(4, () -> call("POST", path + "/recordings", registration(object), "register", 200));
        long recording = registered.getFirst().path("data").path("recordingId").asLong();
        assertThat(registered).allSatisfy(r -> assertThat(r.path("data").path("recordingId").asLong()).isEqualTo(recording));
        call("PATCH", path + "/recordings/" + recording + "/select", null, null, 200);
        long analysis = call("POST", path + "/analyze", Map.of(), "analysis", 200).path("data").path("analysisId").asLong();
        jdbc.update("UPDATE analysis_results SET status='FAILED' WHERE id=?", analysis);
        // A retry key starts one new execution; replaying it must not increment the generation again.
        var retries = parallel(4, () -> call("POST", path + "/analysis/retry", Map.of(), "retry", 200));
        assertThat(retries).allSatisfy(r -> assertThat(r.path("data").path("analysisId").asLong()).isEqualTo(analysis));
        assertThat(jdbc.queryForObject("SELECT retry_count FROM analysis_results WHERE id=?", Integer.class, analysis)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analysis_request_outbox WHERE analysis_id=?", Long.class, analysis)).isEqualTo(2);
    }

    @Test void runningAndSuccessfulAnalysesCannotBeSubmittedAgainOrRetriedPrematurely() throws Exception {
        long session = recordedSession(); String path = "/api/training-sessions/" + session;
        long analysis = call("POST", path + "/analyze", Map.of(), "analysis", 200).path("data").path("analysisId").asLong();
        assertCode(call("POST", path + "/analyze", Map.of(), "different-analysis-key", 409), "ANALYSIS_ALREADY_RUNNING");
        assertCode(call("POST", path + "/analyze", Map.of(), null, 409), "ANALYSIS_ALREADY_RUNNING");
        call("POST", path + "/analysis/retry", Map.of(), "premature-retry", 404);
        jdbc.update("UPDATE analysis_results SET status='COMPLETED' WHERE id=?", analysis);
        assertCode(call("POST", path + "/analyze", Map.of(), "new-key", 409), "ANALYSIS_ALREADY_REQUESTED");
        assertThat(countReceipts("RETRY_ANALYSIS")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analysis_request_outbox WHERE analysis_id=?", Long.class, analysis)).isEqualTo(1);
    }

    @Test void cancelledSessionCannotBeRevivedByFreshUploadOrAnalysisRequests() throws Exception {
        long session = recordedSession(); String path = "/api/training-sessions/" + session;
        call("POST", path + "/cancel", null, null, 200);
        assertCode(call("POST", path + "/recordings/upload-url", uploadBody(), "new-upload", 409), "SESSION_ALREADY_FINISHED");
        // Cancellation also clears selection. Either rejection must leave the session terminal.
        call("POST", path + "/analyze", Map.of(), "analysis", 409);
        assertThat(recovery(session).path("status").asText()).isEqualTo("CANCELED");
        assertThat(countReceipts("REQUEST_ANALYSIS")).isZero();
    }

    @Test void recoveryRequiresAuthenticationAndBoundsStorageProbes() throws Exception {
        long session = session(); String path = "/api/training-sessions/" + session;
        String authenticated = token; token = null;
        try { call("GET", path + "/recovery", null, null, 401); } finally { token = authenticated; }
        for (int index = 0; index < 21; index++)
            call("POST", path + "/recordings/upload-url", uploadBody(), "upload-" + index, 200);
        var state = recovery(session);
        assertThat(state.path("uploads").size()).isEqualTo(20);
        assertThat(state.path("hasMoreUploads").asBoolean()).isTrue();
        verify(storage, times(20)).isUploadedObject(eq(userId), eq(session), anyString(), anyString(), anyLong());
    }

    @Test void anotherSessionsObjectCannotBeRegisteredAndDifferentRegistrationBodyCannotReuseAnObject() throws Exception {
        long first = session(), second = session(); String path = "/api/training-sessions/" + first;
        String object = call("POST", path + "/recordings/upload-url", uploadBody(), "upload", 200).path("data").path("objectKey").asText();
        assertCode(call("POST", "/api/training-sessions/" + second + "/recordings", registration(object), null, 409), "UPLOAD_INTENT_NOT_FOUND");
        call("POST", path + "/recordings", registration(object), null, 200);
        var changed = new HashMap<>(registration(object)); changed.put("durationMs", 2000);
        assertCode(call("POST", path + "/recordings", changed, null, 409), "IDEMPOTENCY_KEY_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM voice_recordings WHERE training_session_id=?", Long.class, first)).isEqualTo(1);
    }

    private Map<String,Object> createBody() { return Map.of("contentId", contentId, "learningFocus", "PRONUNCIATION"); }
    private Map<String,Object> uploadBody() { return Map.of("fileName", "fixture.wav", "mimeType", "audio/wav", "fileSizeBytes", 32044); }
    private Map<String,Object> registration(String key) { return Map.of("objectKey", key, "mimeType", "audio/wav", "fileSizeBytes", 32044, "durationMs", 1000); }
    private long session() throws Exception { return call("POST", "/api/training-sessions", createBody(), UUID.randomUUID().toString(), 200).path("data").path("sessionId").asLong(); }
    private long recordedSession() throws Exception {
        long id = session(); String path = "/api/training-sessions/" + id;
        String object = call("POST", path + "/recordings/upload-url", uploadBody(), "upload", 200).path("data").path("objectKey").asText();
        long recording = call("POST", path + "/recordings", registration(object), "register", 200).path("data").path("recordingId").asLong();
        call("PATCH", path + "/recordings/" + recording + "/select", null, null, 200);
        return id;
    }
    private JsonNode recovery(long session) throws Exception { return call("GET", "/api/training-sessions/" + session + "/recovery", null, null, 200).path("data"); }
    private long countReceipts(String operation) { return jdbc.queryForObject("SELECT count(*) FROM training_request_receipts WHERE user_id=? AND operation=?", Long.class, userId, operation); }
    private String otherAccount() throws Exception {
        String previous = token; token = null;
        try { return call("POST", "/api/auth/signup", Map.of("email", UUID.randomUUID() + "@example.invalid", "password", "Reconnect-fixture-123!", "nickname", "other fixture", "termsAgreed", true, "privacyAgreed", true), null, 200).path("data").path("accessToken").asText(); }
        finally { token = previous; }
    }
    private JsonNode call(String method, String path, Object body, String key, int expected) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (key != null) builder.header("Idempotency-Key", key);
        var response = client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        // Do not expose auth tokens or response bodies in failed assertion output.
        assertThat(response.statusCode()).as(method + " " + path).isEqualTo(expected);
        if (path.endsWith("/recovery") && expected == 200) assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        return json.readTree(response.body());
    }
    private void assertCode(JsonNode response, String code) { assertThat(response.path("code").asText()).isEqualTo(code); }
    private <T> List<T> parallel(int count, Callable<T> work) throws Exception {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new ArrayList<Future<T>>(); for (int i = 0; i < count; i++) tasks.add(pool.submit(work));
            var result = new ArrayList<T>(); for (var task : tasks) result.add(task.get(25, TimeUnit.SECONDS)); return result;
        }
    }
    private void loseAcknowledgement(String path, String body, String key, java.util.function.BooleanSupplier committed) throws Exception {
        var uri = URI.create(base); byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        try (var socket = new Socket("127.0.0.1", uri.getPort())) {
            String headers = "POST " + path + " HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: application/json\r\nAuthorization: Bearer " + token
                    + "\r\nIdempotency-Key: " + key + "\r\nContent-Length: " + raw.length + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII)); socket.getOutputStream().write(raw); socket.getOutputStream().flush();
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!committed.getAsBoolean() && System.nanoTime() < end) Thread.sleep(20);
            assertThat(committed.getAsBoolean()).as("server commits without client reading acknowledgement").isTrue();
        }
    }
}
