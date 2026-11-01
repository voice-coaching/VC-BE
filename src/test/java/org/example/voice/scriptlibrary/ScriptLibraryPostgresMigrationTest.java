package org.example.voice.scriptlibrary;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
class ScriptLibraryPostgresMigrationTest {
    @Test void freshSchemaAndUpgradeBackfillOnlyLivePrivateOriginalsAndEnforceOwnership() throws Exception {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL"),user=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres"),password=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD","");
        for(boolean upgrade:new boolean[]{false,true}) {
            String schema="library_test_"+UUID.randomUUID().toString().replace("-","");
            try(var db=DriverManager.getConnection(url,user,password);var sql=db.createStatement()) {
                try {
                    if(upgrade) {
                        migrate(url,user,password,schema,"56");db.setSchema(schema);
                        sql.executeUpdate("insert into users(id,nickname,status,terms_agreed_at,privacy_agreed_at,created_at,updated_at,role) values (700001,'owner','ACTIVE',now(),now(),now(),now(),'USER'),(700003,'other','ACTIVE',now(),now(),now(),now(),'USER')");
                        sql.executeUpdate("insert into practice_contents(id,owner_id,content_type,learning_focus,title,script_text,custom_title_ciphertext,custom_script_ciphertext,difficulty,status,created_at,updated_at) values (700002,700001,'SENTENCE','PRONUNCIATION','내 문장','[private]','v1:title','v1:script','INTERMEDIATE','HIDDEN',now(),now()),(700005,700001,'SENTENCE','PRONUNCIATION','내 문장','[private]','v1:title2','v1:script2','INTERMEDIATE','HIDDEN',now(),now())");
                        sql.executeUpdate("update practice_contents set custom_title_ciphertext=null,custom_script_ciphertext=null,custom_deleted_at=now() where id=700005");
                    }
                    migrate(url,user,password,schema,"57");db.setSchema(schema);
                    try(var rows=sql.executeQuery("select count(*) from script_library_entries")){rows.next();assertThat(rows.getInt(1)).isEqualTo(upgrade?1:0);}
                    if(upgrade) {
                        try(var rows=sql.executeQuery("select user_id from script_library_entries where content_id=700002")){assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isEqualTo(700001);}
                        assertThatThrownBy(()->sql.executeUpdate("update script_library_entries set user_id=700003 where content_id=700002"))
                                .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23503"));
                        sql.executeUpdate("insert into script_library_topics(id,user_id,name_ciphertext,name_digest) values (800001,700003,'v1:topic','topic-digest')");
                        assertThatThrownBy(()->sql.executeUpdate("update script_library_entries set topic_id=800001 where content_id=700002"))
                                .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23503"));
                        String request="insert into script_library_requests(user_id,key_digest,request_digest,content_id) values (700001,'request','digest',700002)";
                        sql.executeUpdate(request);
                        assertThatThrownBy(()->sql.executeUpdate(request)).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23505"));
                        try(var rows=sql.executeQuery("select custom_script_ciphertext,script_text from practice_contents where id=700002")){rows.next();assertThat(rows.getString(1)).isEqualTo("v1:script");assertThat(rows.getString(2)).isEqualTo("[private]");}
                    }
                } finally {db.setSchema("public");sql.execute("drop schema if exists "+schema+" cascade");}
            }
        }
    }
    private void migrate(String url,String user,String password,String schema,String target){
        var flyway=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load();flyway.migrate();flyway.validate();
    }
}
