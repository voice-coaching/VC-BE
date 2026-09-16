package org.example.voice.notification;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "VC_BE_TEST_POSTGRES_URL", matches = ".+")
class NotificationPostgresMigrationTest {
    @Test void freshDatabaseInstallsNotificationTablesAndIndexes() throws Exception { verify(false); }
    @Test void upgradePreservesExistingContentAndConstrainsNewLinks() throws Exception { verify(true); }
    private void verify(boolean upgrade) throws Exception {
        String url = System.getenv("VC_BE_TEST_POSTGRES_URL"), user = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres"),
                password = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", "");
        String schema = "notification_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var db = DriverManager.getConnection(url, user, password); var sql = db.createStatement()) {
            try {
                if (upgrade) {
                    migrate(url, user, password, schema, "23"); db.setSchema(schema);
                    sql.executeUpdate("insert into practice_contents(id,content_type,learning_focus,title,script_text,difficulty,status,created_at,updated_at) values (700001,'NEWS','BOTH','original title','original text','BEGINNER','PUBLISHED',now(),now())");
                }
                migrate(url, user, password, schema, "24"); db.setSchema(schema);
                for (String table : new String[]{"notification_preferences", "user_notifications", "push_subscriptions", "push_registration_requests"})
                    try (var rows = sql.executeQuery("select count(*) from " + table)) { rows.next(); assertThat(rows.getLong(1)).isZero(); }
                try (var rows = sql.executeQuery("select count(*) from pg_indexes where schemaname=current_schema() and indexname in ('idx_notification_inbox','idx_notification_unread','idx_notification_reminders','idx_push_subscription_owner')")) {
                    rows.next(); assertThat(rows.getInt(1)).isEqualTo(4);
                }
                // CHECK is evaluated before FK: an external link must never enter the inbox.
                assertThatThrownBy(() -> sql.executeUpdate("insert into user_notifications(user_id,type,title,body,deep_link,deduplication_key,created_at) values (999,'PRACTICE_REMINDER','title','body','https://external.invalid','event',now())"))
                        .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
                if (upgrade) try (var rows = sql.executeQuery("select title,script_text from practice_contents where id=700001")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("original title"); assertThat(rows.getString(2)).isEqualTo("original text");
                }
            } finally { db.setSchema("public"); sql.execute("drop schema if exists " + schema + " cascade"); }
        }
    }
    private void migrate(String url, String user, String password, String schema, String target) {
        var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load();
        flyway.migrate(); flyway.validate();
    }
}
