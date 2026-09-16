package org.example.voice.profileimage;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "VC_BE_TEST_POSTGRES_URL", matches = ".+")
class ProfileImagePostgresMigrationTest {
    @Test void freshDatabaseSupportsProfileImages() throws Exception { verify(false); }
    @Test void upgradeFromV18PreservesUsersAndEnforcesOneActiveImage() throws Exception { verify(true); }
    private void verify(boolean upgrade) throws Exception {
        String url = System.getenv("VC_BE_TEST_POSTGRES_URL");
        String user = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres");
        String password = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", "");
        String schema = "photo_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var db = DriverManager.getConnection(url, user, password); var sql = db.createStatement()) {
            try {
                if (upgrade) {
                    flyway(url, user, password, schema, "18").migrate();
                    db.setSchema(schema); sql.executeUpdate(userSql());
                }
                var migration = flyway(url, user, password, schema, "19");
                migration.migrate(); migration.validate(); db.setSchema(schema);
                if (!upgrade) sql.executeUpdate(userSql());
                try (var rows = sql.executeQuery("select profile_image_url from users where id=800001")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isNull();
                }
                sql.executeUpdate(imageSql("one", "ACTIVE"));
                assertThatThrownBy(() -> sql.executeUpdate(imageSql("two", "ACTIVE")))
                        .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23505"));
                sql.executeUpdate(imageSql("two", "UPLOADING"));
                sql.executeUpdate("update profile_images set state='DELETE_PENDING' where object_key='one'");
                sql.executeUpdate("update profile_images set state='ACTIVE' where object_key='two'");
                assertThat(migration.info().current().getVersion().getVersion()).isEqualTo("19");
            } finally {
                db.setSchema("public"); sql.execute("drop schema if exists " + schema + " cascade");
            }
        }
    }
    private Flyway flyway(String url, String user, String password, String schema, String target) {
        return Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load();
    }
    private String userSql() {
        return "insert into users(id,nickname,status,terms_agreed_at,privacy_agreed_at,created_at,updated_at,role) "
                + "values (800001,'photo migration','ACTIVE',now(),now(),now(),now(),'USER')";
    }
    private String imageSql(String key, String state) {
        return "insert into profile_images(user_id,object_key,image_url,original_file_name,size_bytes,request_digest,state,created_at,cleanup_after) "
                + "values (800001,'" + key + "','https://cdn.example.invalid/a.png','a.png',100,'" + "a".repeat(64)
                + "','" + state + "',now(),now())";
    }
}
