package org.example.voice.practicecontent;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
class CustomContentPostgresMigrationTest {
    @Test void freshSchemaAndUpgradePreservePublicContentAndEnforcePrivacy() throws Exception {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL"),user=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres"),password=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD","");
        for(boolean upgrade:new boolean[]{false,true}) {
            String schema="custom_test_"+UUID.randomUUID().toString().replace("-","");
            try(var db=DriverManager.getConnection(url,user,password);var sql=db.createStatement()) {
                try {
                    if(upgrade) {
                        migrate(url,user,password,schema,"21");db.setSchema(schema);
                        sql.executeUpdate("insert into practice_contents(id,content_type,learning_focus,title,script_text,difficulty,status,created_at,updated_at) values (700001,'SENTENCE','BOTH','public','original','BEGINNER','PUBLISHED',now(),now())");
                    }
                    migrate(url,user,password,schema,"22");db.setSchema(schema);
                    if(upgrade) try(var rows=sql.executeQuery("select title,script_text,owner_id from practice_contents where id=700001")) {
                        assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("public");assertThat(rows.getString(2)).isEqualTo("original");assertThat(rows.getObject(3)).isNull();
                    }
                    sql.executeUpdate("insert into users(id,nickname,status,terms_agreed_at,privacy_agreed_at,created_at,updated_at,role) values (700001,'custom migration','ACTIVE',now(),now(),now(),now(),'USER')");
                    String base="insert into practice_contents(id,owner_id,content_type,learning_focus,title,script_text,custom_title_ciphertext,custom_script_ciphertext,difficulty,status,created_at,updated_at) values (700002,700001,'SENTENCE','BOTH','내 문장','[private]','v1:fixture','v1:fixture','INTERMEDIATE',";
                    assertThatThrownBy(()->sql.executeUpdate(base+"'PUBLISHED',now(),now())")).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23514"));
                    sql.executeUpdate(base+"'HIDDEN',now(),now())");
                    String request="insert into custom_content_requests(user_id,key_digest,request_digest,content_id) values (700001,'key','request',700002)";
                    sql.executeUpdate(request);
                    assertThatThrownBy(()->sql.executeUpdate(request)).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23505"));
                } finally {db.setSchema("public");sql.execute("drop schema if exists "+schema+" cascade");}
            }
        }
    }
    private void migrate(String url,String user,String password,String schema,String target){
        var flyway=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load();flyway.migrate();flyway.validate();
    }
}
