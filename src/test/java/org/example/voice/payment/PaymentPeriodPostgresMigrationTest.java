package org.example.voice.payment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "VC_BE_TEST_POSTGRES_URL", matches = ".+")
class PaymentPeriodPostgresMigrationTest {
    private static final List<String> STATES = List.of("PENDING", "PAID", "FAILED", "CANCELLED");
    private static final String START = "TIMESTAMPTZ '2026-10-01 00:00:00+00'";
    private static final String END = "TIMESTAMPTZ '2026-11-01 00:00:00+00'";

    @Test
    void freshAndV53UpgradeEnforceCompletePeriodsWithoutChangingValidHistory() throws Exception {
        for (boolean upgrade : new boolean[]{false, true}) {
            String schema = "payment_period_" + UUID.randomUUID().toString().replace("-", "");
            try (Connection db = connect(); Statement sql = db.createStatement()) {
                try {
                    migrate(schema, upgrade ? "53" : "54");
                    db.setSchema(schema);
                    sql.execute("insert into users(nickname,status,terms_agreed_at,privacy_agreed_at,created_at,updated_at,role) "
                            + "values('period fixture','ACTIVE',now(),now(),now(),now(),'USER')");
                    for (String state : STATES) {
                        sql.execute(insert("valid-" + state, state, START, END));
                        if (!state.equals("PAID")) {
                            sql.execute(insert("empty-" + state, state, "NULL", "NULL"));
                        }
                    }
                    // Preserve exact PAID and historical CANCELLED values across the upgrade.
                    List<String> before = orders(sql);
                    List<String> grantsBefore = grants(sql);
                    if (upgrade) migrate(schema, "54");
                    assertThat(orders(sql)).isEqualTo(before);
                    assertThat(grants(sql)).isEqualTo(grantsBefore);

                    for (String state : STATES) {
                        String id = "invalid-" + state;
                        for (String[] period : new String[][]{
                                {START, "NULL"}, {"NULL", END}, {START, START}, {END, START}}) {
                            rejectsCheck(sql, insert(id, state, period[0], period[1]));
                            rejectsCheck(sql, "update payment_test_orders set paid_at=" + period[0]
                                    + ",expires_at=" + period[1] + " where id='valid-" + state + "'");
                        }
                    }
                    rejectsCheck(sql, insert("empty-paid", "PAID", "NULL", "NULL"));
                    rejectsCheck(sql, "update payment_test_orders set paid_at=NULL,expires_at=NULL where id='valid-PAID'");
                    rejectsCheck(sql, "update payment_test_orders set status='PAID' where id='empty-PENDING'");

                    // A paid period can remain on cancellation, and a nullable draft can be paid atomically.
                    sql.execute("update payment_test_orders set status='CANCELLED' where id='valid-PAID'");
                    sql.execute("update payment_test_orders set status='PAID',paid_at=" + START
                            + ",expires_at=" + END + " where id='empty-PENDING'");
                    assertThat(orders(sql)).hasSize(7);
                    try (var rows = sql.executeQuery("select count(*) from pg_constraint where conrelid='payment_test_orders'::regclass "
                            + "and conname in ('ck_test_payment_period','ck_test_paid_period') and convalidated")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getInt(1)).isEqualTo(2);
                    }
                } finally {
                    db.setSchema("public");
                    sql.execute("drop schema if exists " + schema + " cascade");
                }
            }
        }
    }

    @Test
    void invalidLegacyRowsAbortUpgradeAndLeaveDataAndPriorConstraintsIntact() throws Exception {
        String schema = "payment_period_invalid_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection db = connect(); Statement sql = db.createStatement()) {
            try {
                migrate(schema, "53");
                db.setSchema(schema);
                sql.execute("insert into users(nickname,status,terms_agreed_at,privacy_agreed_at,created_at,updated_at,role) "
                        + "values('legacy period fixture','ACTIVE',now(),now(),now(),now(),'USER')");
                for (String state : STATES) {
                    // Reproduce V49's SQL UNKNOWN loophole on the actual pre-upgrade schema.
                    sql.execute(insert("legacy", state, START, "NULL"));
                    List<String> before = orders(sql);
                    List<String> constraintsBefore = constraints(sql);
                    assertThatThrownBy(() -> migrate(schema, "54"))
                            .satisfies(error -> assertThat(sqlState(error)).isEqualTo("23514"));
                    assertThat(orders(sql)).isEqualTo(before);
                    assertThat(constraints(sql)).isEqualTo(constraintsBefore);
                    try (var rows = sql.executeQuery("select count(*) from flyway_schema_history where version='54'")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getInt(1)).isZero();
                    }
                    // Remove only this test fixture before exercising the next legacy status.
                    sql.execute("delete from payment_test_orders where id='legacy'");
                }
            } finally {
                db.setSchema("public");
                sql.execute("drop schema if exists " + schema + " cascade");
            }
        }
    }

    private static String insert(String id, String state, String paidAt, String expiresAt) {
        return "insert into payment_test_orders(id,user_id,idempotency_key,product,amount,currency,status,created_at,paid_at,expires_at) "
                + "values('" + id + "',1,'" + id + "','SPEAKAI_MONTHLY_TEST_V1',15000,'KRW','" + state
                + "',TIMESTAMPTZ '2026-10-01 00:00:00+00'," + paidAt + "," + expiresAt + ")";
    }

    private static void rejectsCheck(Statement sql, String statement) {
        assertThatThrownBy(() -> sql.execute(statement)).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
    }

    private static List<String> orders(Statement sql) throws SQLException {
        return read(sql, "select row_to_json(o)::text from payment_test_orders o order by id");
    }

    private static List<String> grants(Statement sql) throws SQLException {
        return read(sql, "select row_to_json(g)::text from user_test_membership_grants g order by user_id");
    }

    private static List<String> constraints(Statement sql) throws SQLException {
        return read(sql, "select conname||':'||pg_get_constraintdef(oid) from pg_constraint "
                + "where conrelid='payment_test_orders'::regclass order by conname");
    }

    private static List<String> read(Statement sql, String query) throws SQLException {
        var result = new ArrayList<String>();
        try (var rows = sql.executeQuery(query)) {
            while (rows.next()) result.add(rows.getString(1));
        }
        return result;
    }

    private static String sqlState(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) return sql.getSQLState();
        }
        return null;
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("VC_BE_TEST_POSTGRES_URL"),
                System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres"),
                System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", ""));
    }

    private static void migrate(String schema, String target) {
        Flyway.configure().dataSource(System.getenv("VC_BE_TEST_POSTGRES_URL"),
                        System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres"),
                        System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", ""))
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration")
                .target(target).load().migrate();
    }
}
