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
    @Test void migrationEnqueuesAndFencesGenerationAndPublishesWithoutReviews() throws Exception {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL");
        // This test is deliberately limited to the disposable loopback cluster.
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:15432/");
        String schema="tts_test_"+UUID.randomUUID().toString().replace("-","");
        String user="postgres";
        var flyway=Flyway.configure().dataSource(url,user,"").schemas(schema).defaultSchema(schema).target("30").load();
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
            db.update("INSERT INTO example_tts_profiles(revision,fingerprint) VALUES('r1',?)","a".repeat(64));
            byte[] legacy={73,68,51,1,2};String legacyDigest=ExampleTtsPersistence.hash(legacy);
            db.update("INSERT INTO example_audio_cache(id,audio,etag) VALUES(?,?,?)","b".repeat(64),legacy,'"'+legacyDigest+'"');
            db.update("INSERT INTO example_tts_jobs(example_id,profile_revision,state,attempt,cache_key,audio_sha256,duration_ms) VALUES('tts-5','r1','GENERATED',1,?,?,1000)","b".repeat(64),legacyDigest);
            var upgrade=Flyway.configure().dataSource(url,user,"").schemas(schema).defaultSchema(schema).load();
            upgrade.migrate();upgrade.validate();
            assertThat(db.queryForObject("SELECT content_id FROM example_tts_jobs WHERE example_id='tts-5'",Long.class)).isEqualTo(800015L);
            assertThat(db.queryForObject("SELECT count(*) FROM example_tts_outbox",Integer.class)).isEqualTo(5);
            tx.executeWithoutResult(s->store.reconcile("r1","a".repeat(64)));
            tx.executeWithoutResult(s->store.reconcile("r1","a".repeat(64)));
            assertThat(db.queryForObject("SELECT count(*) FROM example_tts_jobs",Integer.class)).isEqualTo(5);
            var first=tx.execute(s->store.claim("r1").orElseThrow());
            assertThat(store.cached(first)).isPresent();
            db.update("UPDATE example_tts_jobs SET lease_until=now()-interval '1 second' WHERE id=?",first.id());
            var replacement=tx.execute(s->store.claim("r1").orElseThrow());
            assertThat(replacement.id()).isEqualTo(first.id());
            byte[] bytes={73,68,51,1,2}; String digest=ExampleTtsPersistence.hash(bytes);
            var audio=new Generated(bytes,digest,1000);
            Boolean stale=tx.execute(s->store.complete(first,audio));
            Boolean accepted=tx.execute(s->store.complete(replacement,audio));
            assertThat(stale).isFalse();
            assertThat(accepted).isTrue();
            assertThat(store.playable(replacement.exampleId(),"r1")).isPresent();
            assertThat(store.playable(replacement.exampleId(),"other-profile")).isEmpty();
            db.update("INSERT INTO example_audio_approvals(job_id,audio_sha256,reviewer_id,decision) VALUES(?,?,'operator','REJECTED')",replacement.id(),digest);
            assertThat(store.playable(replacement.exampleId(),"r1")).isEmpty();
            db.update("DELETE FROM example_audio_approvals WHERE job_id=?",replacement.id());
            db.update("UPDATE example_audio_cache SET audio=? WHERE id=(SELECT cache_key FROM example_tts_jobs WHERE id=?)",new byte[]{1,2,3},replacement.id());
            assertThatThrownBy(()->store.playable(replacement.exampleId(),"r1")).isInstanceOf(IllegalStateException.class);
            db.update("INSERT INTO practice_contents(id,content_type,learning_focus,title,script_text,difficulty,status,created_at,updated_at) VALUES(900001,'NEWS','PRONUNCIATION','news','version one','BEGINNER','PUBLISHED',now(),now()),(900002,'SENTENCE','PRONUNCIATION','hidden','hidden text','BEGINNER','HIDDEN',now(),now())");
            tx.executeWithoutResult(t->store.reconcile("r1","a".repeat(64)));
            assertThat(db.queryForObject("SELECT count(*) FROM example_tts_jobs",Integer.class)).isEqualTo(6);
            org.example.voice.practiceexample.domain.model.ExampleTtsData.Job news=null;
            for(int i=0;i<5;i++) {
                var claimed=tx.execute(t->store.claim("r1").orElseThrow());
                if(claimed.exampleId().equals("content-900001")) {news=claimed;break;}
                var taken=claimed;tx.executeWithoutResult(t->store.complete(taken,audio));
            }
            assertThat(news).isNotNull();
            var oldNews=news;
            db.update("UPDATE practice_contents SET script_text='version two' WHERE id=900001");
            assertThat(tx.<Boolean>execute(t->store.complete(oldNews,audio))).isFalse();
            tx.executeWithoutResult(t->store.reconcile("r1","a".repeat(64)));
            var newNews=tx.execute(t->store.claim("r1").orElseThrow());
            assertThat(newNews.revision()).isEqualTo(2);
            assertThat(newNews.text()).isEqualTo("version two");
            assertThat(tx.<Boolean>execute(t->store.complete(newNews,audio))).isTrue();
            db.update("INSERT INTO reference_audios(content_id,speaker_name,speaker_type,audio_url,duration_ms,is_primary,created_at) VALUES(900001,'placeholder','COACH','https://cdn.example.com/fake.mp3',1000,true,now())");
            var props=new org.example.voice.practiceexample.application.ExampleTtsProperties();props.setRevision("r1");
            var asset=org.mockito.Mockito.mock(org.example.voice.practiceexample.domain.port.ExampleTtsAssets.class);
            var reader=new org.example.voice.practicecontent.infrastructure.ReferenceAudioReaderImpl(db,props,asset);
            assertThat(reader.findReferenceAudiosByContentId(900001L).items()).hasSize(1);
            assertThat(reader.findReferenceAudiosByContentId(900001L).items().getFirst().speakerType()).isEqualTo(org.example.voice.practicecontent.domain.type.SpeakerType.TTS);
            db.update("UPDATE practice_contents SET status='HIDDEN' WHERE id=900001");
            assertThat(reader.findReferenceAudiosByContentId(900001L).items()).isEmpty();
        } finally {
            try(var db=DriverManager.getConnection(url,user,"");var sql=db.createStatement()) {sql.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
        }
    }
}
