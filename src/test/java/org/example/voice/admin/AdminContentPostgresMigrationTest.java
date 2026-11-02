package org.example.voice.admin;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
class AdminContentPostgresMigrationTest {
    @Test void upgradePinsPublicHistoryWithoutCopyingPrivateTextAndRetainsDeletedRelations() throws Exception {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL"),user=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres"),password=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD","");
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:");
        String schema="admin_content_"+UUID.randomUUID().toString().replace("-","");
        try(var db=DriverManager.getConnection(url,user,password);var sql=db.createStatement()) {
            try {
                migrate(url,user,password,schema,"64");db.setSchema(schema);
                sql.executeUpdate("insert into users(id,nickname,status,role) values(900001,'member','ACTIVE','USER')");
                sql.executeUpdate("insert into practice_contents(id,content_type,script_text,title,status,updated_at,tts_revision) values(900001,'SENTENCE','before edit','original','PUBLISHED',now(),1)");
                sql.executeUpdate("insert into practice_contents(id,content_type,script_text,title,status,owner_id,updated_at,tts_revision) values(900002,'SENTENCE','[private]','private','HIDDEN',900001,now(),1)");
                sql.executeUpdate("insert into training_sessions(id,user_id,content_id,status) values(900001,900001,900001,'COMPLETED'),(900002,900001,900002,'COMPLETED')");
                migrate(url,user,password,schema,"65");db.setSchema(schema);
                try(var rows=sql.executeQuery("select content_script_snapshot,content_updated_at_snapshot from training_sessions where id=900001")) {
                    rows.next();assertThat(rows.getString(1)).isEqualTo("before edit");assertThat(rows.getObject(2)).isNotNull();
                }
                try(var rows=sql.executeQuery("select content_script_snapshot from training_sessions where id=900002")) {
                    rows.next();assertThat(rows.getString(1)).isNull();
                }
                var jdbc=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(db,true));
                sql.executeUpdate("insert into practice_contents(id,content_type,script_text,title,status,difficulty,estimated_seconds,created_at,published_at) values(900003,'NEWS','news','news','PUBLISHED','BEGINNER',5,now(),now())");
                var popularity=new org.example.voice.practicecontent.application.NewsPopularityService(jdbc,java.time.Clock.systemUTC());
                assertThat(popularity.get(org.example.voice.practicecontent.domain.type.ContentType.NEWS,100).items()).anyMatch(i->i.id()==900003L);
                // Guard tombstones independently of publication status, including an existing ranking snapshot.
                sql.executeUpdate("update practice_contents set deleted_at=now() where id=900003");
                assertThat(popularity.get(org.example.voice.practicecontent.domain.type.ContentType.NEWS,100).items()).noneMatch(i->i.id()==900003L);
                sql.executeUpdate("update practice_contents set script_text='after edit',deleted_at=now(),status='HIDDEN' where id=900001");
                sql.executeUpdate("insert into example_tts_jobs(id,content_id,text_revision,profile_revision,state) values(900001,900001,1,'test','RUNNING')");
                var job=new org.example.voice.practiceexample.domain.model.ExampleTtsData.Job(900001,"content-900001","before edit",1,"test",UUID.randomUUID(),1);
                var generated=new org.example.voice.practiceexample.domain.model.ExampleTtsData.Generated(new byte[]{1},"a".repeat(64),1000);
                assertThat(new org.example.voice.practiceexample.infrastructure.ExampleTtsPersistence(jdbc).complete(job,generated)).isFalse();
                try(var rows=sql.executeQuery("select s.content_script_snapshot,c.deleted_at from training_sessions s join practice_contents c on c.id=s.content_id where s.id=900001")) {
                    rows.next();assertThat(rows.getString(1)).isEqualTo("before edit");assertThat(rows.getObject(2)).isNotNull();
                }
            } finally {db.setSchema("public");sql.execute("drop schema if exists "+schema+" cascade");}
        }
    }
    private void migrate(String url,String user,String password,String schema,String target) {
        var flyway=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load();
        flyway.migrate();flyway.validate();
    }
}
