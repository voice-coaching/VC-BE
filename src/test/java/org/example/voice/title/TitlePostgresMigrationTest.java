package org.example.voice.title;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
class TitlePostgresMigrationTest {
    @Test void freshDatabaseInstallsPoliciesAndExamConstraints() throws Exception { verify(false); }
    @Test void upgradeFromV19PreservesExistingUserAndPhotoSchema() throws Exception { verify(true); }
    private void verify(boolean upgrade) throws Exception {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL"), user=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres"),
                password=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD","");
        String schema="title_test_"+UUID.randomUUID().toString().replace("-","");
        try(var db=DriverManager.getConnection(url,user,password);var sql=db.createStatement()) {
            try {
                if(upgrade) { flyway(url,user,password,schema,"19").migrate(); db.setSchema(schema); sql.executeUpdate(userSql()); }
                var migration=flyway(url,user,password,schema,"20"); migration.migrate(); migration.validate(); db.setSchema(schema);
                if(!upgrade) sql.executeUpdate(userSql());
                try(var rows=sql.executeQuery("select count(*) from title_policies")) { rows.next(); assertThat(rows.getInt(1)).isEqualTo(4); }
                try(var rows=sql.executeQuery("select profile_image_url from users where id=700001")) { assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isNull(); }
                sql.executeUpdate("insert into practice_contents(id,content_type,learning_focus,title,script_text,difficulty,status,created_at,updated_at) values (700001,'SENTENCE','BOTH','exam','test','BEGINNER','PUBLISHED',now(),now())");
                String insert="insert into title_exams(user_id,previous_rank,target_rank,practice_content_id,required_training_count,passing_score,status,created_at) values (700001,'ABSOLUTE_BEGINNER','BEGINNER',700001,5,70,'READY',now())";
                sql.executeUpdate(insert);
                assertThatThrownBy(()->sql.executeUpdate(insert)).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23505"));
                sql.executeUpdate("update title_exams set status='FAILED'"); sql.executeUpdate(insert);
                assertThat(migration.info().current().getVersion().getVersion()).isEqualTo("20");
            } finally {db.setSchema("public"); sql.execute("drop schema if exists "+schema+" cascade");}
        }
    }
    private Flyway flyway(String url,String user,String password,String schema,String target) {
        return Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load();
    }
    private String userSql(){return "insert into users(id,nickname,status,terms_agreed_at,privacy_agreed_at,created_at,updated_at,role) values (700001,'title migration','ACTIVE',now(),now(),now(),now(),'USER')";}
}
