package org.example.voice.admin;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
class AdminAudioPostgresMigrationTest {
    @Test void upgradeKeepsExistingAudiosAndEnforcesIntentStateAndContentBinding() throws Exception {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL"),user=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres"),password=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD","");
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:");
        String schema="admin_audio_"+UUID.randomUUID().toString().replace("-","");
        try(var db=DriverManager.getConnection(url,user,password);var sql=db.createStatement()) {
            try {
                migrate(url,user,password,schema,"61");db.setSchema(schema);
                long before;try(var rows=sql.executeQuery("select count(*) from reference_audios")){rows.next();before=rows.getLong(1);}
                migrate(url,user,password,schema,"62");db.setSchema(schema);
                try(var rows=sql.executeQuery("select count(*) from reference_audios")){rows.next();assertThat(rows.getLong(1)).isEqualTo(before);}
                sql.executeUpdate("insert into users(id,nickname,status,role,terms_agreed_at,privacy_agreed_at,created_at,updated_at) values(900001,'operator','ACTIVE','ADMIN',now(),now(),now(),now())");
                var contents=new ArrayList<Long>();
                try(var rows=sql.executeQuery("select id from practice_contents where content_type='ANNOUNCER' order by id limit 2")){while(rows.next())contents.add(rows.getLong(1));}
                assertThat(contents).hasSize(2);
                long audio;
                try(var rows=sql.executeQuery("insert into reference_audios(content_id,speaker_name,speaker_type,object_key,duration_ms,is_primary,created_at) values("+contents.get(0)+",'announcer','ANNOUNCER','reference-audios/announcer/test.m4a',1000,true,now()) returning id")){rows.next();audio=rows.getLong(1);}
                String pending="insert into admin_reference_audio_uploads(id,actor_user_id,content_id,extension,content_type,size_bytes,sha256,script_sha256,created_at,expires_at,state) values('"+UUID.randomUUID()+"',900001,"+contents.get(0)+",'.m4a','audio/mp4',100,'"+"a".repeat(64)+"','"+"b".repeat(64)+"',now(),now()+interval '10 minutes','PENDING')";
                sql.executeUpdate(pending);
                assertThatThrownBy(()->sql.executeUpdate("update admin_reference_audio_uploads set state='COMPLETED'"))
                        .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23514"));
                assertThatThrownBy(()->sql.executeUpdate("update admin_reference_audio_uploads set size_bytes=20971521"))
                        .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23514"));
                assertThatThrownBy(()->sql.executeUpdate("update admin_reference_audio_uploads set content_type='audio/mpeg'"))
                        .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23514"));
                sql.executeUpdate("update admin_reference_audio_uploads set reference_audio_id="+audio+",state='COMPLETED',completion_digest='"+"c".repeat(64)+"'");
                assertThatThrownBy(()->sql.executeUpdate("update admin_reference_audio_uploads set content_id="+contents.get(1)))
                        .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23503"));
            } finally {db.setSchema("public");sql.execute("drop schema if exists "+schema+" cascade");}
        }
    }
    private void migrate(String url,String user,String password,String schema,String target) {
        var flyway=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load();
        flyway.migrate();flyway.validate();
    }
}
