package org.example.voice.course;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "VC_BE_TEST_POSTGRES_URL", matches = ".+")
class CoursePracticeCleanupPostgresMigrationTest {
    @Test
    void upgradeRetiresLegacyStagesAndPreservesSentencesRevisionsAndSessionLinks() throws Exception {
        String url = System.getenv("VC_BE_TEST_POSTGRES_URL");
        String user = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres");
        String password = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", "");
        String schema = "course_cleanup_" + UUID.randomUUID().toString().replace("-", "");
        try (var db = DriverManager.getConnection(url, user, password); var sql = db.createStatement()) {
            try {
                migration(url, user, password, schema, "52").migrate();
                db.setSchema(schema);
                sql.executeUpdate("insert into course_steps(course_id,step_type,step_order,title,required) "
                        + "select (select min(course_id) from course_steps),type,ordinal,title,true "
                        + "from (values ('AUDIO_EXAMPLE',3,'legacy audio'),('RESULT_REVIEW',4,'legacy review')) extra(type,ordinal,title)");
                sql.executeUpdate("insert into users(id,nickname,status,terms_agreed_at,privacy_agreed_at,created_at,updated_at,role) values (900001,'cleanup','ACTIVE',now(),now(),now(),now(),'USER')");
                // A historical theory session must remain pinned to its original immutable revision.
                sql.executeUpdate("insert into training_sessions(id,user_id,content_id,course_step_id,course_education_revision_id,learning_focus,status,started_at) "
                        + "select 900001,900001,e.content_id,s.id,r.id,'BOTH','RECORDING',now() "
                        + "from course_steps s join course_step_revisions r on r.step_id=s.id "
                        + "cross join practice_examples e where s.step_type='THEORY' order by s.id,e.id limit 1");
                long steps = count(sql, "select count(*) from course_steps");
                long revisions = count(sql, "select count(*) from course_step_revisions");
                long examples = count(sql, "select count(*) from practice_examples");
                long originalRevision = count(sql, "select course_education_revision_id from training_sessions where id=900001");

                sql.executeUpdate("insert into courses(id,course_type,title,difficulty,status,created_at,updated_at) values (900001,'PRONUNCIATION','not ready','BEGINNER','PUBLISHED',now(),now())");
                sql.executeUpdate("insert into course_steps(course_id,step_type,step_order,title,required) values (900001,'THEORY',1,'not ready',true),(900001,'PRACTICE',2,'no sentences',true)");
                var upgrade = migration(url, user, password, schema, "53");
                upgrade.migrate();
                upgrade.validate();
                assertThat(count(sql, "select count(*) from course_steps")).isEqualTo(steps + 2);
                assertThat(count(sql, "select count(*) from course_step_revisions")).isEqualTo(revisions);
                assertThat(count(sql, "select count(*) from practice_examples")).isEqualTo(examples);
                assertThat(count(sql, "select count(*) from course_steps where retired")).isEqualTo(8);
                assertThat(count(sql, "select count(*) from course_steps where retired and required")).isZero();
                assertThat(count(sql, "select count(*) from course_steps where step_type='PRACTICE' and retired")).isZero();
                assertThat(count(sql, "select count(*) from course_steps where course_id=900001 and retired")).isZero();
                assertThat(count(sql, "select course_education_revision_id from training_sessions where id=900001")).isEqualTo(originalRevision);
                assertThat(count(sql, "select count(*) from training_sessions session join course_steps step on step.id=session.course_step_id where session.id=900001 and step.retired")).isEqualTo(1);
                // Flyway re-run must not create or remove content.
                upgrade.migrate();
                assertThat(count(sql, "select count(*) from practice_examples")).isEqualTo(examples);
            } finally {
                db.setSchema("public");
                sql.execute("drop schema if exists " + schema + " cascade");
            }
        }
    }

    private long count(java.sql.Statement sql, String query) throws Exception {
        try (var rows = sql.executeQuery(query)) {
            assertThat(rows.next()).isTrue();
            return rows.getLong(1);
        }
    }

    private Flyway migration(String url, String user, String password, String schema, String target) {
        return Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load();
    }
}
