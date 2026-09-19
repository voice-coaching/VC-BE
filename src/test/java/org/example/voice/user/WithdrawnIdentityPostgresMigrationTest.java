package org.example.voice.user;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "VC_BE_TEST_POSTGRES_URL", matches = ".+")
class WithdrawnIdentityPostgresMigrationTest {

    @Test
    void migrationReleasesIdentifiersHeldByExistingWithdrawnUsers() throws Exception {
        String url = System.getenv("VC_BE_TEST_POSTGRES_URL");
        String username = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres");
        String password = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", "");
        String schema = "withdrawn_identity_test_" + UUID.randomUUID().toString().replace("-", "");

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            try {
                migrate(url, username, password, schema, "27");
                connection.setSchema(schema);
                statement.executeUpdate("""
                        INSERT INTO users (id, email, password, nickname, status, terms_agreed_at,
                                           privacy_agreed_at, created_at, updated_at, role)
                        VALUES (800001, 'returning@example.com', 'hash', 'old-user', 'WITHDRAWN',
                                now(), now(), now(), now(), 'USER')
                        """);
                statement.executeUpdate("""
                        INSERT INTO social_accounts (user_id, provider, provider_user_id, provider_email, created_at)
                        VALUES (800001, 'NAVER', 'returning-provider-id', 'returning@example.com', now())
                        """);

                migrate(url, username, password, schema, "28");
                connection.setSchema(schema);

                try (var user = statement.executeQuery(
                        "SELECT email, password FROM users WHERE id = 800001")) {
                    assertThat(user.next()).isTrue();
                    assertThat(user.getString("email")).isNull();
                    assertThat(user.getString("password")).isNull();
                }
                try (var accounts = statement.executeQuery(
                        "SELECT count(*) FROM social_accounts WHERE user_id = 800001")) {
                    assertThat(accounts.next()).isTrue();
                    assertThat(accounts.getLong(1)).isZero();
                }
                statement.executeUpdate("""
                        INSERT INTO users (email, password, nickname, status, terms_agreed_at,
                                           privacy_agreed_at, created_at, updated_at, role)
                        VALUES ('returning@example.com', 'new-hash', 'new-user', 'ACTIVE',
                                now(), now(), now(), now(), 'USER')
                        """);
            } finally {
                connection.setSchema("public");
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private void migrate(String url, String username, String password, String schema, String target) {
        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load();
        flyway.migrate();
        flyway.validate();
    }
}
