package org.example.voice.practiceexample;

import java.sql.*;
import java.util.*;
import org.example.voice.practiceexample.domain.model.ExampleTtsData.Generated;
import org.example.voice.practiceexample.infrastructure.ExampleTtsPersistence;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL", matches=".+")
class ExampleTtsPostgresTest {
    @Test void migrationEnqueuesAndFencesGenerationAndRequiresDigestBoundReviews() throws Exception {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL");
        // This test is deliberately limited to the disposable loopback cluster.
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:15432/");
        String schema="tts_test_"+UUID.randomUUID().toString().replace("-","");
        String user="postgres";
        var flyway=Flyway.configure().dataSource(url,user,"").schemas(schema).defaultSchema(schema).load();
        try {
            flyway.migrate(); flyway.validate();
            var source=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,"");
            var db=new JdbcTemplate(source);
            var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            var store=new ExampleTtsPersistence(db);
            tx.executeWithoutResult(status->{
                db.update("INSERT INTO courses(id,course_type,title,difficulty,status,created_at,updated_at) VALUES(800001,'PRONUNCIATION','test','BEGINNER','PUBLISHED',now(),now())");
                db.update("INSERT INTO course_steps(id,course_id,step_type,step_order,title,required) VALUES(800001,800001,'PRACTICE',1,'test',true)");
                db.update("INSERT INTO course_step_revisions(id,step_id,revision,title,step_order,step_type,blocks_json) VALUES(800001,800001,1,'test',1,'PRACTICE','[]')");
                db.update("INSERT INTO practice_example_sets(id,step_id,revision,education_revision_id,published_at) VALUES(800001,800001,1,800001,now())");
                for(int i=1;i<=5;i++) {
                    db.update("INSERT INTO practice_contents(id,content_type,learning_focus,title,script_text,difficulty,status,created_at,updated_at) VALUES(?,'CLASS_PRACTICE','PRONUNCIATION','test','test text','BEGINNER','PUBLISHED',now(),now())",800010+i);
                    db.update("INSERT INTO practice_examples(id,set_id,content_id,example_order,locale) VALUES(?,800001,?,?,'ko-KR')","tts-"+i,800010+i,i);
                }
            });
            assertThat(db.queryForObject("SELECT count(*) FROM example_tts_outbox",Integer.class)).isEqualTo(5);
            tx.executeWithoutResult(s->store.reconcile("r1","a".repeat(64)));
            tx.executeWithoutResult(s->store.reconcile("r1","a".repeat(64)));
            assertThat(db.queryForObject("SELECT count(*) FROM example_tts_jobs",Integer.class)).isEqualTo(5);
            var first=tx.execute(s->store.claim("r1").orElseThrow());
            db.update("UPDATE example_tts_jobs SET lease_until=now()-interval '1 second' WHERE id=?",first.id());
            var replacement=tx.execute(s->store.claim("r1").orElseThrow());
            assertThat(replacement.id()).isEqualTo(first.id());
            byte[] bytes={73,68,51,1,2}; String digest=ExampleTtsPersistence.hash(bytes);
            var audio=new Generated(bytes,digest,1000);
            Boolean stale=tx.execute(s->store.complete(first,audio));
            Boolean accepted=tx.execute(s->store.complete(replacement,audio));
            assertThat(stale).isFalse();
            assertThat(accepted).isTrue();
            assertThat(store.approved(replacement.exampleId(),"r1")).isEmpty();
            db.update("INSERT INTO example_audio_approvals(job_id,audio_sha256,reviewer_id,decision) VALUES(?,?,'one','APPROVED')",replacement.id(),digest);
            assertThat(store.approved(replacement.exampleId(),"r1")).isEmpty();
            db.update("INSERT INTO example_audio_approvals(job_id,audio_sha256,reviewer_id,decision) VALUES(?,?,'two','APPROVED')",replacement.id(),digest);
            assertThat(store.approved(replacement.exampleId(),"r1")).isPresent();
            db.update("UPDATE example_audio_cache SET audio=? WHERE id=(SELECT cache_key FROM example_tts_jobs WHERE id=?)",new byte[]{1,2,3},replacement.id());
            assertThatThrownBy(()->store.approved(replacement.exampleId(),"r1")).isInstanceOf(IllegalStateException.class);
        } finally {
            try(var db=DriverManager.getConnection(url,user,"");var sql=db.createStatement()) {sql.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
        }
    }
}
