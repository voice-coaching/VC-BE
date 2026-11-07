package org.example.voice.admin;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
class AdminTitleExamPolicyPostgresTest {
    @Test void upgradePreservesExistingPolicyAndDefaultsTheEditVersion() throws Exception {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL"), user=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres");
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:");
        String schema="admin_title_"+UUID.randomUUID().toString().replace("-","");
        try(var db=DriverManager.getConnection(url,user,"");var sql=db.createStatement()) {
            try {
                migrate(url,user,schema,"66");db.setSchema(schema);
                sql.executeUpdate("update title_policies set passing_score=77 where target_rank='BEGINNER'");
                migrate(url,user,schema,"67");db.setSchema(schema);
                try(var rows=sql.executeQuery("select passing_score,edit_version from title_policies where target_rank='BEGINNER'")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(77);assertThat(rows.getLong(2)).isZero();
                }
                assertThatThrownBy(()->sql.executeUpdate("update title_policies set edit_version=null"))
                        .isInstanceOf(java.sql.SQLException.class);
            } finally { sql.execute("drop schema if exists "+schema+" cascade"); }
        }
    }
    private void migrate(String url,String user,String schema,String target) {
        Flyway.configure().dataSource(url,user,"").schemas(schema).locations("classpath:db/migration").target(target).load().migrate();
    }
}
