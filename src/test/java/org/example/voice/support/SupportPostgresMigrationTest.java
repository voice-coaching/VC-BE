package org.example.voice.support;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "VC_BE_TEST_POSTGRES_URL", matches = ".+")
class SupportPostgresMigrationTest {
    @Test void freshMigrationCreatesSupportTablesAndConstraints() throws Exception { verify(null); }
    @Test void upgradeFromV16PreservesExistingUsersAndAddsSupportTables() throws Exception { verify("16"); }
    @Test void upgradeFromV17PreservesExistingUsersAndAddsSupportTables() throws Exception { verify("17"); }

    private void verify(String previousVersion) throws Exception {
        String url = System.getenv("VC_BE_TEST_POSTGRES_URL");
        String user = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres");
        String password = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", "");
        String schema = "support_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection db = DriverManager.getConnection(url, user, password)) {
            try {
                if (previousVersion != null) {
                    flyway(url, user, password, schema, previousVersion).migrate();
                    db.setSchema(schema);
                    insertUser(db);
                }
                var migrations = flyway(url, user, password, schema, "18");
                migrations.migrate();
                migrations.validate();
                db.setSchema(schema);
                if (previousVersion == null) insertUser(db);
                try (var query = db.createStatement(); var rows = query.executeQuery(
                        "select count(*) from information_schema.columns where table_schema = current_schema() "
                                + "and table_name = 'analysis_results' and column_name = 'execution_deadline_at'")) {
                    rows.next(); assertThat(rows.getInt(1)).isEqualTo(1);
                }
                try (var query = db.createStatement(); var rows = query.executeQuery("select count(*) from users where id=900001")) {
                    rows.next(); assertThat(rows.getInt(1)).isEqualTo(1);
                }
                try (var sql = db.createStatement()) {
                    sql.executeUpdate("insert into notices(title,summary,sections,pinned,published,published_at) "
                            + "values ('공지','요약','[{\"title\":\"안내\",\"paragraphs\":[\"내용\"]}]',true,true,now())");
                    sql.executeUpdate(inquirySql("'" + "a".repeat(64) + "'"));
                    assertThatThrownBy(() -> sql.executeUpdate(inquirySql("'" + "a".repeat(64) + "'")))
                            .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23505"));
                    sql.executeUpdate(inquirySql("null"));
                    sql.executeUpdate(inquirySql("null"));
                }
                assertThat(migrations.info().current().getVersion().getVersion()).isEqualTo("18");
            } finally {
                // Only the randomly generated schema owned by this test is removed.
                db.setSchema("public");
                try (var cleanup = db.createStatement()) { cleanup.execute("drop schema if exists " + schema + " cascade"); }
            }
        }
    }

    private static Flyway flyway(String url, String user, String password, String schema, String target) {
        return Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load();
    }
    private static void insertUser(Connection db) throws SQLException {
        try (var sql = db.createStatement()) {
            sql.executeUpdate("insert into users(id,nickname,status,terms_agreed_at,privacy_agreed_at,created_at,updated_at,role) "
                    + "values (900001,'migration user','ACTIVE',now(),now(),now(),now(),'USER')");
        }
    }
    private static String inquirySql(String key) {
        return "insert into inquiries(user_id,category,subject,body,status,created_at,idempotency_key_sha256,request_sha256) "
                + "values (900001,'ANALYSIS','문의','내용','RECEIVED',now()," + key + ",'" + "b".repeat(64) + "')";
    }
}
