package org.example.voice.practiceexample;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "VC_BE_TEST_POSTGRES_URL", matches = ".+")
class PracticeExamplePostgresMigrationTest {
    @Test void freshInstallsEmptyExampleCatalogAndQuotaControl() throws Exception { verify(false); }
    @Test void upgradePreservesExistingContentWithoutInventingExamples() throws Exception { verify(true); }
    @Test void variableCountMigrationAcceptsTenContiguousExamples() throws Exception {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL"),user=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres"),password=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD","");
        String schema="variable_examples_test_"+UUID.randomUUID().toString().replace("-","");
        try(var db=DriverManager.getConnection(url,user,password);var sql=db.createStatement()) {
            try {
                migrate(url,user,password,schema,"45");db.setSchema(schema);db.setAutoCommit(false);
                long stepId;
                try(var rows=sql.executeQuery("""
                        select step.id from course_steps step
                        join courses course on course.id=step.course_id
                        where course.title='억양 기초 클래스' and step.step_type='PRACTICE' and step.step_order=2
                        """)){rows.next();stepId=rows.getLong(1);}
                long educationRevisionId;
                try(var rows=sql.executeQuery("""
                        insert into course_step_revisions(step_id,revision,title,subtitle,step_order,step_type,blocks_json)
                        select step_id,2,title,subtitle,step_order,step_type,blocks_json
                        from course_step_revisions where step_id=%d and revision=1 returning id
                        """.formatted(stepId))){rows.next();educationRevisionId=rows.getLong(1);}
                long setId;
                try(var rows=sql.executeQuery("""
                        insert into practice_example_sets(step_id,revision,education_revision_id,published_at)
                        values (%d,2,%d,now()) returning id
                        """.formatted(stepId,educationRevisionId))){rows.next();setId=rows.getLong(1);}
                for(int order=1;order<=10;order++){
                    long contentId=9_900_000L+order;
                    sql.executeUpdate("""
                            insert into practice_contents(id,content_type,learning_focus,category,title,script_text,difficulty,status,published_at,created_at,updated_at)
                            values (%d,'CLASS_PRACTICE','INTONATION','INTONATION_CLASS','가변 예문 %d','가변 개수 검증 문장 %d','BEGINNER','PUBLISHED',now(),now(),now())
                            """.formatted(contentId,order,order));
                    sql.executeUpdate("""
                            insert into practice_examples(id,set_id,content_id,example_order,locale)
                            values ('variable-r2-%d',%d,%d,%d,'ko-KR')
                            """.formatted(order,setId,contentId,order));
                }
                db.commit();db.setAutoCommit(true);
                try(var rows=sql.executeQuery("select count(*) from practice_examples where set_id="+setId)){
                    rows.next();assertThat(rows.getInt(1)).isEqualTo(10);
                }
            } finally { db.setAutoCommit(true);db.setSchema("public");sql.execute("drop schema if exists "+schema+" cascade"); }
        }
    }
    private void verify(boolean upgrade) throws Exception {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL"),user=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres"),password=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD","");
        String schema="examples_test_"+UUID.randomUUID().toString().replace("-","");
        try(var db=DriverManager.getConnection(url,user,password);var sql=db.createStatement()) {
            try {
                if(upgrade) {
                    migrate(url,user,password,schema,"24");db.setSchema(schema);
                    sql.executeUpdate("insert into practice_contents(id,content_type,learning_focus,title,script_text,difficulty,status,created_at,updated_at) values (700001,'NEWS','BOTH','original title','original text','BEGINNER','PUBLISHED',now(),now())");
                }
                migrate(url,user,password,schema,"25");db.setSchema(schema);
                try(var rows=sql.executeQuery("select count(*) from practice_example_sets")){rows.next();assertThat(rows.getLong(1)).isZero();}
                try(var rows=sql.executeQuery("select count(*) from example_tts_quotas where id=0")){rows.next();assertThat(rows.getLong(1)).isEqualTo(1);}
                try(var rows=sql.executeQuery("select count(*) from information_schema.triggers where trigger_schema=current_schema() and trigger_name in ('example_set_complete','example_items_complete','example_content_immutable')")) {
                    rows.next();assertThat(rows.getInt(1)).isEqualTo(3);
                }
                if(upgrade)try(var rows=sql.executeQuery("select title,script_text from practice_contents where id=700001")){
                    assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("original title");assertThat(rows.getString(2)).isEqualTo("original text");
                }
            } finally { db.setSchema("public");sql.execute("drop schema if exists "+schema+" cascade"); }
        }
    }
    private void migrate(String url,String user,String password,String schema,String target) {
        var flyway=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load();flyway.migrate();flyway.validate();
    }
}
