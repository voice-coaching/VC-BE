package org.example.voice.admin;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
class AdminTitleExamPolicyPostgresTest {
    @Test void newPublishedQuestionGetsItsOwnTtsJobWithoutRetiringOriginalAudio() throws Exception {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL"), user=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres");
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:");
        String schema="admin_title_script_"+UUID.randomUUID().toString().replace("-","");
        try(var db=DriverManager.getConnection(url,user,"");var sql=db.createStatement()) {
            try {
                migrate(url,user,schema,"latest");db.setSchema(schema);
                sql.executeUpdate("insert into practice_contents(id,content_type,category,status,title,script_text,tts_revision) values(900101,'SENTENCE','TITLE_EXAM','PUBLISHED','original','original script',1),(900102,'SENTENCE','TITLE_EXAM','PUBLISHED','revised','revised script',1)");
                sql.executeUpdate("insert into reference_audios(id,content_id,speaker_type,object_key) values(900101,900101,'TTS','original-audio.mp3')");
                sql.executeUpdate("insert into title_exams(id,user_id,practice_content_id,status,passing_score) values(900101,900101,900101,'IN_PROGRESS',70)");
                sql.executeUpdate("update title_policies set practice_content_id=900102 where target_rank='BEGINNER'");
                var jdbc=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(db,true));
                var tts=new org.example.voice.practiceexample.infrastructure.ExampleTtsPersistence(jdbc);
                int rounds=jdbc.queryForObject("select count(*)/100+2 from practice_contents",Integer.class);
                for(int i=0;i<rounds;i++) tts.reconcile("script-edit-test","script-edit-test");
                assertThat(jdbc.queryForObject("select count(*) from example_tts_jobs where content_id=900102 and text_revision=1 and profile_revision='script-edit-test' and state='PENDING'",Integer.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject("select script_text from practice_contents where id=900102",String.class)).isEqualTo("revised script");
                assertThat(jdbc.queryForObject("select script_text from practice_contents c join title_exams e on e.practice_content_id=c.id where e.id=900101",String.class)).isEqualTo("original script");
                assertThat(jdbc.queryForObject("select object_key from reference_audios where id=900101 and retired_at is null",String.class)).isEqualTo("original-audio.mp3");
            } finally { db.setSchema("public");sql.execute("drop schema if exists "+schema+" cascade"); }
        }
    }
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
