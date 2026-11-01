package org.example.voice.coaching;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL", matches=".+")
class CoachingUsageMigrationPostgresTest {
    @Test void freshAndUpgradePreserveHistoricalResultsAndFixedTrialRollout() throws Exception {
        for(boolean upgrade:new boolean[]{false,true}) {
            String schema="coaching_"+UUID.randomUUID().toString().replace("-","");
            try(var db=DriverManager.getConnection(url(),user(),password());var sql=db.createStatement()) {
                try {
                    migrate(schema,upgrade?"58":"59");db.setSchema(schema);
                    sql.execute("INSERT INTO users(nickname,status,terms_agreed_at,privacy_agreed_at,created_at,updated_at,role) VALUES('coaching fixture','ACTIVE',now(),now(),now()-interval '1 year',now(),'USER')");
                    sql.execute("INSERT INTO training_sessions(user_id,content_id,learning_focus,status,started_at) SELECT 1,min(id),'PRONUNCIATION','RECORDING',now() FROM practice_contents");
                    sql.execute("INSERT INTO voice_recordings(training_session_id,attempt_no,audio_url,quality_status,created_at) VALUES(1,1,'fixture.wav','PASS',now())");
                    sql.execute("INSERT INTO analysis_results(recording_id,status,created_at,analysis_outcome) VALUES(1,'COMPLETED',now(),'COACHING_READY')");
                    String before=scalar(sql,"SELECT status||':'||analysis_outcome FROM analysis_results WHERE id=1");
                    if(upgrade)migrate(schema,"59");
                    assertThat(scalar(sql,"SELECT status||':'||analysis_outcome FROM analysis_results WHERE id=1")).isEqualTo(before);
                    assertThat(scalar(sql,"SELECT count(*) FROM coaching_usage_reservations")).isEqualTo("0");
                    String rollout=scalar(sql,"SELECT effective_at::text FROM coaching_usage_policy");
                    migrate(schema,"59");
                    assertThat(scalar(sql,"SELECT effective_at::text FROM coaching_usage_policy")).isEqualTo(rollout);
                    assertThat(scalar(sql,"SELECT coaching_delivered FROM analysis_results WHERE id=1")).isEqualTo("f");
                    assertThatThrownBy(()->sql.execute("INSERT INTO coaching_usage_policy VALUES(2,now())"))
                            .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23514"));
                    sql.execute("INSERT INTO coaching_usage_reservations VALUES(1,1,1,'"+UUID.randomUUID()+"','free',now(),now()+interval '1 month','CONSUMED')");
                    sql.execute("DELETE FROM analysis_results WHERE id=1");
                    assertThat(scalar(sql,"SELECT state FROM coaching_usage_reservations WHERE analysis_id=1")).isEqualTo("CONSUMED");
                } finally {db.setSchema("public");sql.execute("DROP SCHEMA "+schema+" CASCADE");}
            }
        }
    }
    private static String scalar(Statement sql,String q)throws SQLException {try(var r=sql.executeQuery(q)){assertThat(r.next()).isTrue();return r.getString(1);}}
    private static void migrate(String schema,String target) {Flyway.configure().dataSource(url(),user(),password()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load().migrate();}
    private static String url(){return System.getenv("VC_BE_TEST_POSTGRES_URL");}
    private static String user(){return System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres");}
    private static String password(){return System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD","");}
}
