package org.example.voice.admin;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
class AdminPostgresMigrationTest {
    @Test void upgradePreservesDataDefaultsVersionsAndProtectsAuditFromMutation() throws Exception {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL"),user=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres"),password=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD","");
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:");
        String schema="admin_test_"+UUID.randomUUID().toString().replace("-","");
        try(var db=DriverManager.getConnection(url,user,password);var sql=db.createStatement()) {
            try {
                migrate(url,user,password,schema,"61");db.setSchema(schema);
                long before;try(var rows=sql.executeQuery("select count(*) from practice_contents")){rows.next();before=rows.getLong(1);}
                sql.executeUpdate("insert into users(id,nickname,status,role,terms_agreed_at,privacy_agreed_at,created_at,updated_at) values(900001,'operator','ACTIVE','ADMIN',now(),now(),now(),now())");
                migrate(url,user,password,schema,"62");db.setSchema(schema);
                try(var rows=sql.executeQuery("select count(*),count(*) filter(where version=0) from practice_contents")) {
                    rows.next();assertThat(rows.getLong(1)).isEqualTo(before);assertThat(rows.getLong(2)).isEqualTo(before);
                }
                sql.executeUpdate("insert into admin_audit_logs(actor_user_id,action,target_type,target_id,reason,changed_fields,outcome,correlation_id,created_at) values(900001,'CONTENT_EDIT','CONTENT','1','test','title','SUCCESS','test',now())");
                for(String command:new String[]{"update admin_audit_logs set reason='overwrite'","delete from admin_audit_logs","truncate admin_audit_logs"})
                    assertThatThrownBy(()->sql.executeUpdate(command)).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23514"));
                // Existing native insert paths do not have to know the new optimistic version field.
                sql.executeUpdate("insert into practice_contents(content_type,learning_focus,category,title,script_text,difficulty,status,created_at,updated_at) values('SENTENCE','PRONUNCIATION','FINAL_CONSONANT','native','native','BEGINNER','DRAFT',now(),now())");
                try(var rows=sql.executeQuery("select version from practice_contents where title='native'")){rows.next();assertThat(rows.getLong(1)).isZero();}
            } finally {db.setSchema("public");sql.execute("drop schema if exists "+schema+" cascade");}
        }
    }
    private void migrate(String url,String user,String password,String schema,String target) {
        var flyway=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load();
        flyway.migrate();flyway.validate();
    }
}
