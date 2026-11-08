package org.example.voice.notification.infrastructure;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Transactional outbox. FCM calls happen outside database transactions. */
@Component
@Slf4j
@ConditionalOnProperty(name = "notification.native-fcm.enabled", havingValue = "true")
public class NativeFcmDispatcher {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final NativeFcmTokenCipher cipher;
    private final String projectId;
    private final Duration pollInterval;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "native-fcm-dispatch");
        thread.setDaemon(true);
        return thread;
    });

    public NativeFcmDispatcher(JdbcTemplate jdbc, TransactionTemplate transactions, NativeFcmTokenCipher cipher,
                               @Value("${notification.native-fcm.project-id}") String projectId,
                               @Value("${notification.native-fcm.poll-interval:PT15S}") Duration pollInterval) {
        if (projectId.isBlank() || pollInterval.compareTo(Duration.ofSeconds(1)) < 0)
            throw new IllegalArgumentException("Invalid native FCM configuration");
        this.jdbc = jdbc; this.transactions = transactions; this.cipher = cipher; this.projectId = projectId;
        this.pollInterval = pollInterval;
    }

    private record Job(long id, UUID lease, long deviceId, String token, String title, String body, String link) {}

    @PostConstruct
    void start() {
        executor.scheduleWithFixedDelay(() -> {
            try { dispatch(); }
            catch (Exception error) { log.warn("Native FCM worker failed; next poll will retry ({}).", error.getClass().getSimpleName()); }
        }, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() { executor.shutdownNow(); }

    public void dispatch() {
        for (int i = 0; i < 10; i++) {
            Job job = transactions.execute(status -> claim());
            if (job == null) return;
            try {
                String token = cipher.decrypt(job.token());
                Message message = Message.builder().setToken(token)
                        .setNotification(Notification.builder().setTitle(job.title()).setBody(job.body()).build())
                        .putData("deepLink", safeLink(job.link())).build();
                messaging().send(message);
                complete(job, "SENT");
            } catch (FirebaseMessagingException error) {
                if (error.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    transactions.executeWithoutResult(status -> jdbc.update("delete from native_fcm_devices where id=? and encrypted_token=?", job.deviceId(), job.token()));
                } else {
                    log.warn("Native FCM dispatch rejected (code={}).", error.getMessagingErrorCode());
                    retry(job, retryAfterSeconds(error));
                }
            } catch (Exception error) {
                log.warn("Native FCM dispatch failed; retry scheduled ({}).", error.getClass().getSimpleName());
                retry(job, 0);
            }
        }
    }

    private Job claim() {
        UUID lease = UUID.randomUUID();
        var rows = jdbc.query("""
            UPDATE native_fcm_outbox SET state='SENDING', lease_id=?, lease_until=now()+interval '5 minutes', attempts=attempts+1
             WHERE id=(SELECT id FROM native_fcm_outbox WHERE
                 ((state='PENDING' AND available_at<=now()) OR (state='SENDING' AND lease_until<now()))
                 AND created_at>now()-interval '24 hours' ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1)
             RETURNING id,device_id
            """, (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2)}, lease);
        if (rows.isEmpty()) return null;
        long[] row = rows.getFirst();
        var jobs = jdbc.query("""
            SELECT d.encrypted_token,n.title,n.body,n.deep_link FROM native_fcm_outbox o
            JOIN native_fcm_devices d ON d.id=o.device_id
            JOIN user_notifications n ON n.id=o.notification_id AND n.user_id=d.user_id
            WHERE o.id=? AND o.lease_id=?
            """, (rs, n) -> new Job(row[0], lease, row[1], rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), row[0], lease);
        return jobs.isEmpty() ? null : jobs.getFirst();
    }

    private void complete(Job job, String state) {
        transactions.executeWithoutResult(status -> jdbc.update("update native_fcm_outbox set state=?,lease_id=null,lease_until=null where id=? and lease_id=?", state, job.id(), job.lease()));
    }

    private void retry(Job job, long retryAfterSeconds) {
        transactions.executeWithoutResult(status -> jdbc.update("""
            update native_fcm_outbox set state=case when attempts>=8 then 'FAILED' else 'PENDING' end,
              available_at=now()+(greatest(?,least(3600,60*power(2,least(attempts-1,6)))) * interval '1 second'),
              lease_id=null,lease_until=null where id=? and lease_id=?
            """, retryAfterSeconds, job.id(), job.lease()));
    }

    private long retryAfterSeconds(FirebaseMessagingException error) {
        if (error.getHttpResponse() == null) return 0;
        Object value = error.getHttpResponse().getHeaders().entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase("Retry-After"))
                .map(java.util.Map.Entry::getValue).findFirst().orElse(null);
        return parseRetryAfter(value, java.time.Instant.now());
    }

    static long parseRetryAfter(Object value, java.time.Instant now) {
        if (value instanceof java.util.List<?> list) value = list.isEmpty() ? null : list.getFirst();
        if (value == null) return 0;
        try {
            long seconds = Long.parseLong(value.toString().trim());
            return Math.max(0, Math.min(86400, seconds));
        } catch (NumberFormatException ignored) {
            try {
                long millis = Duration.between(now, ZonedDateTime.parse(value.toString(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis();
                long seconds = millis <= 0 ? 0 : (millis + 999) / 1000;
                return Math.max(0, Math.min(86400, seconds));
            } catch (RuntimeException invalid) { return 0; }
        }
    }

    protected FirebaseMessaging messaging() throws Exception {
        synchronized (NativeFcmDispatcher.class) {
            FirebaseApp app = FirebaseApp.getApps().stream().filter(it -> it.getName().equals("native-fcm")).findFirst()
                    .orElse(null);
            if (app == null) app = FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault()).setProjectId(projectId)
                    .setConnectTimeout(5000).setReadTimeout(10000).setWriteTimeout(10000).build(), "native-fcm");
            return FirebaseMessaging.getInstance(app);
        }
    }

    private String safeLink(String link) {
        return switch (link == null ? "" : link) {
            case "/home" -> "/home";
            case "/mypage" -> "/mypage";
            default -> "/home/notifications";
        };
    }
}
