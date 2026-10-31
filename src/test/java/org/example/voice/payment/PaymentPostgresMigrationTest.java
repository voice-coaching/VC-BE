package org.example.voice.payment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "VC_BE_TEST_POSTGRES_URL", matches = ".+")
class PaymentPostgresMigrationTest {
    @Test void membershipGrantSnapshotsOnlyExistingActiveUsersAndPreservesRoles() throws Exception {
        var url = System.getenv("VC_BE_TEST_POSTGRES_URL");
        var user = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres");
        var password = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", "");
        var schema = "membership_" + UUID.randomUUID().toString().replace("-", "");
        try (var db = DriverManager.getConnection(url, user, password); var sql = db.createStatement()) {
            try {
                var config = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                        .locations("classpath:db/migration");
                config.target("51").load().migrate();
                db.setSchema(schema);
                String insert = "insert into users(nickname,status,terms_agreed_at,privacy_agreed_at,created_at,updated_at,role) values";
                sql.execute(insert + "('existing','ACTIVE',now(),now(),now(),now(),'USER'),"
                        + "('operator','ACTIVE',now(),now(),now(),now(),'ADMIN'),"
                        + "('suspended','SUSPENDED',now(),now(),now(),now(),'USER'),"
                        + "('withdrawn','WITHDRAWN',now(),now(),now(),now(),'USER')");
                config.target("52").load().migrate();
                try (var rows = sql.executeQuery("select count(*) from user_test_membership_grants")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(2);
                }
                sql.execute(insert + "('new','ACTIVE',now(),now(),now(),now(),'USER')");
                config.target("52").load().migrate();
                try (var rows = sql.executeQuery("select count(*) from user_test_membership_grants g join users u on u.id=g.user_id where u.nickname='new'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isZero();
                }
                try (var rows = sql.executeQuery("select count(*) from users where role='ADMIN'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                }
                assertThatThrownBy(() -> sql.execute("insert into user_test_membership_grants values(999999,now())"))
                        .isInstanceOf(SQLException.class);
            } finally {
                db.setSchema("public");
                sql.execute("drop schema if exists " + schema + " cascade");
            }
        }
    }
    @Test void cancellationMigrationPreservesOrdersAndEnforcesOneRequestPerOrder() throws Exception {
        var url = System.getenv("VC_BE_TEST_POSTGRES_URL");
        var user = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres");
        var password = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", "");
        for (boolean upgrade : new boolean[]{false, true}) {
            var schema = "cancellation_" + UUID.randomUUID().toString().replace("-", "");
            try (var db = DriverManager.getConnection(url, user, password); var sql = db.createStatement()) {
                try {
                    var config = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                            .locations("classpath:db/migration");
                    if (upgrade) config.target("50").load().migrate();
                    config.target("51").load().migrate();
                    db.setSchema(schema);
                    sql.execute("insert into users(nickname,status,terms_agreed_at,privacy_agreed_at,created_at,updated_at,role) values('test','ACTIVE',now(),now(),now(),now(),'USER')");
                    sql.execute("insert into payment_test_orders(id,user_id,idempotency_key,product,amount,currency,status,created_at) values('original',1,'key','SPEAKAI_MONTHLY_TEST_V1',15000,'KRW','PENDING',now())");
                    sql.execute("insert into payment_test_cancellations values('first','original','request','UNKNOWN',now(),now())");
                    assertThatThrownBy(() -> sql.execute("insert into payment_test_cancellations values('second','original','request','UNKNOWN',now(),now())"))
                            .isInstanceOf(SQLException.class);
                    assertThatThrownBy(() -> sql.execute("update payment_test_cancellations set status='invalid'"))
                            .isInstanceOf(SQLException.class);
                    assertThatThrownBy(() -> sql.execute("update payment_test_cancellations set order_id='not-owned'"))
                            .isInstanceOf(SQLException.class);
                    try (var rows = sql.executeQuery("select status,amount from payment_test_orders where id='original'")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).isEqualTo("PENDING");
                        assertThat(rows.getLong(2)).isEqualTo(15000);
                    }
                } finally {
                    db.setSchema("public");
                    sql.execute("drop schema if exists " + schema + " cascade");
                }
            }
        }
    }
    @Test void historyIndexSupportsFreshAndV49UpgradeWithoutChangingLedger() throws Exception {
        var url = System.getenv("VC_BE_TEST_POSTGRES_URL");
        var user = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres");
        var password = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", "");
        for (boolean upgrade : new boolean[]{false, true}) {
            var schema = "history_" + UUID.randomUUID().toString().replace("-", "");
            try (var db = DriverManager.getConnection(url, user, password); var sql = db.createStatement()) {
                try {
                    var config = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                            .locations("classpath:db/migration");
                    if (upgrade) {
                        config.target("49").load().migrate(); db.setSchema(schema);
                        sql.execute("insert into users(nickname,status,terms_agreed_at,privacy_agreed_at,created_at,updated_at,role) values('test','ACTIVE',now(),now(),now(),now(),'USER')");
                        sql.execute("insert into payment_test_orders(id,user_id,idempotency_key,product,amount,currency,status,created_at) values('preserved',1,'key','SPEAKAI_MONTHLY_TEST_V1',15000,'KRW','PENDING',now())");
                    }
                    config.target("50").load().migrate(); db.setSchema(schema);
                    try (var rows = sql.executeQuery("select pg_get_indexdef('ix_test_payment_history'::regclass)")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).contains("(user_id, created_at DESC, id DESC)");
                    }
                    try (var rows = sql.executeQuery("select count(*) from payment_test_orders")) {
                        assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(upgrade ? 1 : 0);
                    }
                } finally { db.setSchema("public"); sql.execute("drop schema if exists " + schema + " cascade"); }
            }
        }
    }
    @Test void freshSchemaAndExistingV48UpgradeEnforceFinancialInvariants() throws Exception {
        for (boolean upgrade : new boolean[]{false, true}) {
            var url = System.getenv("VC_BE_TEST_POSTGRES_URL");
            var user = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres");
            var password = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", "");
            var schema = "payment_test_" + UUID.randomUUID().toString().replace("-", "");
            try (var db = DriverManager.getConnection(url, user, password); var sql = db.createStatement()) {
                try {
                    var config = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                            .locations("classpath:db/migration");
                    if (upgrade) config.target("48").load().migrate();
                    config.target("49").load().migrate(); db.setSchema(schema);
                    sql.execute("insert into users(nickname,status,terms_agreed_at,privacy_agreed_at,created_at,updated_at,role) values('test','ACTIVE',now(),now(),now(),now(),'USER')");
                    String prefix = "insert into payment_test_orders(id,user_id,idempotency_key,product,amount,currency,status,created_at) values(";
                    sql.execute(prefix + "'first',1,'unique','SPEAKAI_MONTHLY_TEST_V1',15000,'KRW','PENDING',now())");
                    assertThatThrownBy(() -> sql.execute(prefix + "'duplicate',1,'unique','SPEAKAI_MONTHLY_TEST_V1',15000,'KRW','PENDING',now())"))
                            .isInstanceOf(SQLException.class);
                    assertThatThrownBy(() -> sql.execute(prefix + "'cheap',1,'other','SPEAKAI_MONTHLY_TEST_V1',1,'KRW','PENDING',now())"))
                            .isInstanceOf(SQLException.class);
                    assertThatThrownBy(() -> sql.execute("update payment_test_orders set status='PAID' where id='first'"))
                            .isInstanceOf(SQLException.class);
                    assertThatThrownBy(() -> sql.execute("update payment_test_orders set paid_at=now(),expires_at=now()-interval '1 day' where id='first'"))
                            .isInstanceOf(SQLException.class);
                    try (var rows = sql.executeQuery("select count(*) from payment_test_orders")) {
                        assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(1);
                    }
                } finally { db.setSchema("public"); sql.execute("drop schema if exists " + schema + " cascade"); }
            }
        }
    }
}
