package org.example.voice.course;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
class CourseEducationPostgresMigrationTest {
    @Test void freshDatabaseHasRevisionSchema() throws Exception { verify(false); }
    @Test void upgradePreservesBodyAndPinsExistingSessions() throws Exception { verify(true); }
    private void verify(boolean upgrade) throws Exception {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL"), user=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres"),
                password=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD","");
        String schema="course_test_"+UUID.randomUUID().toString().replace("-","");
        try(var db=DriverManager.getConnection(url,user,password);var sql=db.createStatement()) {
            try {
                if(upgrade) {
                    flyway(url,user,password,schema,"20").migrate(); db.setSchema(schema);
                    sql.executeUpdate("insert into users(id,nickname,status,terms_agreed_at,privacy_agreed_at,created_at,updated_at,role) values (700001,'course migration','ACTIVE',now(),now(),now(),now(),'USER')");
                    sql.executeUpdate("insert into practice_contents(id,content_type,learning_focus,title,script_text,difficulty,status,created_at,updated_at) values (700001,'SENTENCE','BOTH','text','test','BEGINNER','PUBLISHED',now(),now())");
                    sql.executeUpdate("insert into courses(id,course_type,title,difficulty,status,created_at,updated_at) values (700001,'PRONUNCIATION','course','BEGINNER','PUBLISHED',now(),now())");
                    sql.executeUpdate("insert into course_steps(id,course_id,practice_content_id,step_type,step_order,title,body,required) values (700001,700001,700001,'PRACTICE',1,'step','existing body',true)");
                    sql.executeUpdate("insert into training_sessions(id,user_id,content_id,course_step_id,learning_focus,status,started_at) values (700001,700001,700001,700001,'BOTH','RECORDING',now())");
                }
                var migration=flyway(url,user,password,schema,"21"); migration.migrate(); migration.validate(); db.setSchema(schema);
                assertThat(migration.info().current().getVersion().getVersion()).isEqualTo("21");
                if(upgrade) {
                    try(var rows=sql.executeQuery("select r.revision,r.blocks_json::jsonb->0->>'body' as body,jsonb_array_length(r.blocks_json::jsonb) as count from training_sessions s join course_step_revisions r on r.id=s.course_education_revision_id where s.id=700001")) {
                        assertThat(rows.next()).isTrue(); assertThat(rows.getInt("revision")).isEqualTo(1);
                        assertThat(rows.getString("body")).isEqualTo("existing body"); assertThat(rows.getInt("count")).isEqualTo(2);
                    }
                    assertThatThrownBy(()->sql.executeUpdate("update course_step_revisions set title='changed'"))
                            .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23514"));
                    assertThatThrownBy(()->sql.executeUpdate("delete from course_step_revisions"))
                            .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23514"));
                    sql.executeUpdate("insert into course_step_revisions(step_id,revision,title,step_order,step_type,blocks_json) values (700001,2,'new',1,'PRACTICE','[]')");
                }
            } finally {db.setSchema("public");sql.execute("drop schema if exists "+schema+" cascade");}
        }
    }
    private Flyway flyway(String url,String user,String password,String schema,String target) {
        return Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load();
    }
}
