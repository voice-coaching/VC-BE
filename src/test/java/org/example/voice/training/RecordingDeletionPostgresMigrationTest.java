package org.example.voice.training;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "VC_BE_TEST_POSTGRES_URL", matches = ".+")
class RecordingDeletionPostgresMigrationTest {
    private static final List<String> LEGACY_REASONS = List.of(
            "RECORDING_DELETED", "SESSION_CANCELED", "HISTORY_DELETED", "USER_WITHDRAWN", "UPLOAD_EXPIRED");

    @Test
    void freshDatabaseAcceptsAnalysisCompletedAndRetainsOtherConstraints() throws Exception {
        verify(false);
    }

    @Test
    void upgradePreservesPendingDeletionsAndAllowsAnalysisCompletion() throws Exception {
        verify(true);
    }

    private void verify(boolean upgrade) throws Exception {
        String url = System.getenv("VC_BE_TEST_POSTGRES_URL");
        String user = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres");
        String password = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", "");
        String schema = "deletion_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var db = DriverManager.getConnection(url, user, password); var sql = db.createStatement()) {
            try {
                if (upgrade) {
                    migrate(url, user, password, schema, "25");
                    db.setSchema(schema);
                    for (String reason : LEGACY_REASONS) insert(db, reason, "PENDING", 2);
                    assertThatThrownBy(() -> insert(db, "ANALYSIS_COMPLETED", "PENDING", 0))
                            .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
                }
                migrate(url, user, password, schema, "26");
                db.setSchema(schema);
                if (upgrade) {
                    try (var rows = sql.executeQuery("select reason, status, attempt_count from recording_deletion_outbox order by reason")) {
                        var remaining = new java.util.HashSet<>(LEGACY_REASONS);
                        while (rows.next()) {
                            assertThat(remaining.remove(rows.getString(1))).isTrue();
                            assertThat(rows.getString(2)).isEqualTo("PENDING");
                            assertThat(rows.getInt(3)).isEqualTo(2);
                        }
                        assertThat(remaining).isEmpty();
                    }
                }
                for (String reason : LEGACY_REASONS) insert(db, reason, "PENDING", 0);
                insert(db, "ANALYSIS_COMPLETED", "PENDING", 0);
                try (var rows = sql.executeQuery("select count(*) from recording_deletion_outbox where reason='ANALYSIS_COMPLETED'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                }
                assertCheckViolation(db, "UNKNOWN_REASON", "PENDING", 0);
                assertCheckViolation(db, "ANALYSIS_COMPLETED", "UNKNOWN_STATUS", 0);
                assertCheckViolation(db, "ANALYSIS_COMPLETED", "PENDING", 11);
            } finally {
                db.setSchema("public");
                sql.execute("drop schema if exists " + schema + " cascade");
            }
        }
    }

    private void assertCheckViolation(Connection db, String reason, String status, int attempts) {
        assertThatThrownBy(() -> insert(db, reason, status, attempts))
                .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
    }

    private void insert(Connection db, String reason, String status, int attempts) throws SQLException {
        try (var statement = db.prepareStatement("""
                insert into recording_deletion_outbox
                    (user_id, training_session_id, object_key, reason, status, attempt_count)
                values (1, 1, ?, ?, ?, ?)
                """)) {
            statement.setString(1, "test/" + UUID.randomUUID());
            statement.setString(2, reason);
            statement.setString(3, status);
            statement.setInt(4, attempts);
            statement.executeUpdate();
        }
    }

    private void migrate(String url, String user, String password, String schema, String target) {
        var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load();
        flyway.migrate();
        flyway.validate();
    }
}
