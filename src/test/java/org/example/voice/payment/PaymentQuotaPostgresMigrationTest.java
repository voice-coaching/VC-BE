package org.example.voice.payment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "VC_BE_TEST_POSTGRES_URL", matches = ".+")
class PaymentQuotaPostgresMigrationTest {
    @Test void freshAndUpgradePreserveHistoryAndEnforceBoundedOwnerScopedQuota() throws Exception {
        for (boolean upgrade : new boolean[]{false, true}) {
            String schema = "payment_quota_" + UUID.randomUUID().toString().replace("-", "");
            try (var db = DriverManager.getConnection(url(), user(), password()); var sql = db.createStatement()) {
                try {
                    migrate(schema, upgrade ? "54" : "55");
                    db.setSchema(schema);
                    sql.execute("insert into users(nickname,status,terms_agreed_at,privacy_agreed_at,created_at,updated_at,role) "
                            + "values('quota fixture','ACTIVE',now(),now(),now(),now(),'USER')");
                    sql.execute("insert into payment_test_orders(id,user_id,idempotency_key,product,amount,currency,status,created_at) "
                            + "values('quota-history',1,'quota-history','SPEAKAI_MONTHLY_TEST_V1',15000,'KRW','PENDING',now())");
                    String before = scalar(sql, "select row_to_json(o)::text from payment_test_orders o where id='quota-history'");
                    if (upgrade) migrate(schema, "55");
                    assertThat(scalar(sql, "select row_to_json(o)::text from payment_test_orders o where id='quota-history'")).isEqualTo(before);
                    assertThat(scalar(sql, "select count(*) from payment_request_quotas")).isEqualTo("0");
                    for (var operation : org.example.voice.payment.domain.PaymentOperation.values()) {
                        String name = operation.name();
                        sql.execute("insert into payment_request_quotas values('1:" + name + "',1,'" + name + "',now()," + operation.limit() + ")");
                        rejects(sql, "update payment_request_quotas set attempts=" + (operation.limit() + 1) + " where operation='" + name + "'", "23514");
                    }
                    rejects(sql, "update payment_request_quotas set attempts=-1", "23514");
                    rejects(sql, "update payment_request_quotas set attempts=NULL", "23502");
                    rejects(sql, "update payment_request_quotas set window_started_at=NULL", "23502");
                    rejects(sql, "update payment_request_quotas set operation='UNKNOWN' where operation='CREATE'", "23514");
                    rejects(sql, "insert into payment_request_quotas values('999:READ',999,'READ',now(),1)", "23503");
                    rejects(sql, "insert into payment_request_quotas values('1:READ',1,'READ',now(),1)", "23505");
                    assertThat(scalar(sql, "select count(*) from payment_request_quotas")).isEqualTo("4");
                    sql.execute("delete from payment_test_orders where id='quota-history'");
                    sql.execute("delete from users where id=1");
                    assertThat(scalar(sql, "select count(*) from payment_request_quotas")).isEqualTo("0");
                } finally {
                    db.setSchema("public");
                    sql.execute("drop schema if exists " + schema + " cascade");
                }
            }
        }
    }

    private static void rejects(Statement sql, String statement, String sqlState) {
        assertThatThrownBy(() -> sql.execute(statement)).isInstanceOfSatisfying(SQLException.class,
                e -> assertThat(e.getSQLState()).isEqualTo(sqlState));
    }
    private static String scalar(Statement sql, String query) throws SQLException {
        try (var result = sql.executeQuery(query)) { assertThat(result.next()).isTrue(); return result.getString(1); }
    }
    private static String url() { return System.getenv("VC_BE_TEST_POSTGRES_URL"); }
    private static String user() { return System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres"); }
    private static String password() { return System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", ""); }
    private static void migrate(String schema, String target) {
        Flyway.configure().dataSource(url(), user(), password()).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate();
    }
}
