package org.example.voice.admin;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
class AdminMemberDeletionPostgresTest {
    @Test void databaseErasureIsAtomicIsolatedIdempotentAndKeepsSharedContent() {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL"),user=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres"),password=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD","");
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:");
        String schema="admin_erasure_"+UUID.randomUUID().toString().replace("-","");
        var root=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        try {
            var flyway=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load();
            flyway.migrate();flyway.validate();
            var db=new JdbcTemplate(new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password));
            long target=member(db),other=member(db),actor=member(db);
            long publicCount=count(db,"practice_contents","owner_id is null");
            var own=chain(db,target);var unrelated=chain(db,other);
            UUID orphanExecution=UUID.randomUUID(),orphanReceipt=UUID.randomUUID();
            db.update("insert into analysis_evidence_receipts(receipt_id,execution_id,request_id,analysis_id,worker_instance_id,manifest_sha256,manifest_bytes) values(?,?,?,?,?,?,?)",orphanReceipt,orphanExecution,UUID.randomUUID(),own.analysis(),UUID.randomUUID(),"a".repeat(64),new byte[]{123});
            long campaign=db.queryForObject("insert into admin_notification_campaigns(audience,kind,title,body,deep_link,created_by,created_at) values('SELECTED_USERS','GENERAL','shared','shared','/home',?,now()) returning id",Long.class,target);
            db.update("insert into admin_notification_recipients(campaign_id,user_id) values(?,?),(?,?)",campaign,target,campaign,other);
            db.update("insert into admin_popups(title,body,priority,starts_at,ends_at,enabled,created_by,created_at) values('shared','shared',1,now(),now()+interval '1 day',true,?,now())",target);
            db.update("insert into admin_audit_logs(actor_user_id,action,target_type,target_id,reason,changed_fields,outcome,correlation_id,created_at) values(?,'CONTENT_EDIT','CONTENT','1','shared','title','SUCCESS',?,now())",target,UUID.randomUUID().toString());
            db.update("insert into payment_test_orders(id,user_id,idempotency_key,product,amount,currency,status,created_at) values('owned-order',?,'test','SPEAKAI_MONTHLY_TEST_V1',15000,'KRW','PENDING',now())",target);
            db.update("insert into payment_test_cancellations(id,order_id,reason,status,requested_at,updated_at) values('owned-cancel','owned-order','test','PENDING',now(),now())");
            db.update("insert into inquiries(user_id,category,subject,body,status,created_at,request_sha256) values(?,'GENERAL','private subject','private body','RECEIVED',now(),?)",target,"c".repeat(64));
            db.update("insert into profile_images(user_id,object_key,image_url,original_file_name,size_bytes,request_digest,state,created_at,cleanup_after) values(?,'private/profile','https://private.invalid/image','private.png',1024,?,'ACTIVE',now(),now()+interval '1 day')",target,"d".repeat(64));
            long privateContent=db.queryForObject("insert into practice_contents(content_type,learning_focus,title,script_text,status,owner_id,custom_script_ciphertext,custom_title_ciphertext,created_at) values('CUSTOM','PRONUNCIATION','내 문장','[private]','HIDDEN',?,'encrypted-test','encrypted-test',now()) returning id",Long.class,target);
            db.update("insert into example_tts_profiles(revision,fingerprint) values('purge-test',?)","e".repeat(64));
            db.update("insert into example_audio_cache(id,audio,etag) values('private-cache',?,'test-etag')",new byte[]{1,2,3});
            db.update("insert into example_tts_jobs(profile_revision,content_id,state,cache_key,audio_sha256,duration_ms) values('purge-test',?,'GENERATED','private-cache',?,1000)",privateContent,"e".repeat(64));
            long topic=db.queryForObject("insert into script_library_topics(user_id,name_ciphertext,name_digest) values(?,'encrypted-test',?) returning id",Long.class,target,"f".repeat(64));
            db.update("insert into script_library_entries(content_id,user_id,topic_id,created_at) values(?,?,?,now())",privateContent,target,topic);
            long inconsistent=db.queryForObject("insert into training_sessions(user_id,content_id,learning_focus,status,started_at) values(?,?,'PRONUNCIATION','RECORDING',now()) returning id",Long.class,other,privateContent);
            assertThatThrownBy(()->db.queryForObject("select admin_purge_member_database(?)",Object.class,target)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(count(db,"users","id="+target)).isEqualTo(1);
            db.update("delete from training_sessions where id=?",inconsistent);
            UUID exclusive=direct(db,target),shared=direct(db,target);
            db.update("insert into direct_analysis_history_links(user_id,job_id,claim_sha256) values(?,?,?)",other,shared,"a".repeat(64));
            // Unknown future FK dependencies must roll back every previous DELETE, not partially erase.
            db.execute("create table safety_hold(user_id bigint references users(id))");db.update("insert into safety_hold values(?)",target);
            assertThatThrownBy(()->db.queryForObject("select admin_purge_member_database(?)",Object.class,target)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(count(db,"users","id="+target)).isEqualTo(1);
            assertThat(count(db,"analysis_canonical_executions","execution_id='"+own.execution()+"'")).isEqualTo(1);
            assertThat(count(db,"admin_member_deletion_receipts","user_id="+target)).isZero();
            db.update("delete from safety_hold where user_id=?",target);
            Object deletedAt=db.queryForObject("select admin_purge_member_database(?)",Object.class,target);
            assertThat(deletedAt).isNotNull();
            assertThat(db.queryForObject("select admin_purge_member_database(?)",Object.class,target)).isEqualTo(deletedAt);
            assertThat(count(db,"analysis_evidence_receipts","receipt_id='"+orphanReceipt+"'")).isZero();
            assertThat(count(db,"users","id="+target)).isZero();assertThat(count(db,"users","id="+other)).isEqualTo(1);
            assertThat(count(db,"analysis_canonical_executions","execution_id='"+own.execution()+"'")).isZero();
            assertThat(count(db,"analysis_canonical_executions","execution_id='"+unrelated.execution()+"'")).isEqualTo(1);
            assertThat(count(db,"analysis_canonical_results","execution_id='"+unrelated.execution()+"'")).isEqualTo(1);
            assertThat(count(db,"direct_analysis_history","job_id='"+exclusive+"'")).isZero();
            assertThat(count(db,"direct_analysis_history","job_id='"+shared+"'")).isEqualTo(1);
            assertThat(count(db,"payment_test_cancellations","order_id='owned-order'")).isZero();
            assertThat(count(db,"practice_contents","owner_id is null")).isEqualTo(publicCount);
            assertThat(count(db,"practice_contents","id="+privateContent)).isZero();
            assertThat(count(db,"example_audio_cache","id='private-cache'")).isZero();
            assertThat(db.queryForObject("select created_by is null from admin_notification_campaigns where id=?",Boolean.class,campaign)).isTrue();
            assertThat(count(db,"admin_notification_recipients","user_id="+other)).isEqualTo(1);
            assertThat(count(db,"admin_audit_logs","actor_user_id="+target)).isZero();
            // Check every ordinary user_id table, including tables intentionally lacking a user FK.
            for(String table:db.queryForList("select table_name from information_schema.columns where table_schema=? and column_name='user_id'",String.class,schema)) {
                if(!table.equals("admin_member_deletion_receipts")) assertThat(count(db,table,"user_id="+target)).as(table).isZero();
            }
            assertThat(count(db,"users","id="+actor)).isEqualTo(1);
        } finally { root.execute("drop schema if exists "+schema+" cascade"); }
    }
    record Chain(long session,long recording,long analysis,UUID execution) {}
    private Chain chain(JdbcTemplate db,long user) {
        long content=db.queryForObject("select min(id) from practice_contents where owner_id is null",Long.class);
        long session=db.queryForObject("insert into training_sessions(user_id,content_id,learning_focus,status,started_at) values(?,?,'PRONUNCIATION','RECORDING',now()) returning id",Long.class,user,content);
        long recording=db.queryForObject("insert into voice_recordings(training_session_id,attempt_no,audio_url,quality_status,created_at) values(?,1,'private/recording','PENDING',now()) returning id",Long.class,session);
        long analysis=db.queryForObject("insert into analysis_results(recording_id,status,created_at) values(?,'PROCESSING',now()) returning id",Long.class,recording);
        UUID execution=UUID.randomUUID(),request=UUID.randomUUID(),event=UUID.randomUUID(),worker=UUID.randomUUID(),receipt=UUID.randomUUID(),handoff=UUID.randomUUID();String hash="a".repeat(64);byte[] bytes={123};
        db.update("insert into analysis_canonical_executions(execution_id,request_id,analysis_id,recording_id,content_id,analysis_profile,request_schema_version,result_schema_version,request_payload_sha256,script_sha256,audio_sha256,deadline_at) values(?,?,?,?,?,'CANONICAL_FROZEN_20260928_V4','voice-coaching.runpod-analysis-request.v2','voice-coaching.runpod-analysis-result.v4',?,?,?,now()+interval '1 hour')",execution,request,analysis,recording,content,hash,hash,hash);
        db.update("insert into analysis_evidence_receipts(receipt_id,execution_id,request_id,analysis_id,worker_instance_id,manifest_sha256,manifest_bytes) values(?,?,?,?,?,?,?)",receipt,execution,request,analysis,worker,hash,bytes);
        db.update("insert into analysis_canonical_callback_inbox(event_id,execution_id,request_id,analysis_id,worker_instance_id,payload_sha256,raw_sha256,callback_bytes,status,verified_at) values(?,?,?,?,?,?,?,?,'VERIFIED',now())",event,execution,request,analysis,worker,hash,hash,bytes);
        db.update("insert into analysis_canonical_results(event_id,execution_id,request_id,analysis_id,recording_id,content_id,worker_instance_id,schema_version,analysis_profile,status,payload_sha256,raw_sha256,callback_bytes,callback_document) values(?,?,?,?,?,?,?,'voice-coaching.runpod-analysis-result.v4','CANONICAL_FROZEN_20260928_V4','FAILED',?,?,?,'{}')",event,execution,request,analysis,recording,content,worker,hash,hash,bytes);
        db.update("insert into analysis_canonical_result_effects(event_id,analysis_id,user_id,session_id) values(?,?,?,?)",event,analysis,user,session);
        db.update("insert into analysis_canonical_callback_apply(event_id) values(?)",event);
        db.update("insert into analysis_canonical_ack_journal(event_id,disposition,ack_bytes) values(?,'APPLIED',?)",event,bytes);
        db.update("insert into analysis_canonical_journals(execution_id,request_id,analysis_id,worker_instance_id,event_id,worker_revision,pipeline_revision,request_bytes,request_raw_sha256) values(?,?,?,?,?,'test','test',?,?)",execution,request,analysis,worker,event,bytes,hash);
        db.update("insert into analysis_canonical_upload_journal(execution_id,kind,metadata_bytes,sha256,byte_size) values(?,'CORE',?,?,1)",execution,bytes,hash);
        db.update("insert into analysis_canonical_handoffs(handoff_id,execution_id,event_id,request_id,analysis_id,worker_instance_id,metadata_bytes,projection_bytes,handoff_sha256,reserved_bytes) values(?,?,?,?,?,?,?,?,?,85065728)",handoff,execution,event,request,analysis,worker,bytes,bytes,hash);
        db.update("insert into analysis_canonical_handoff_artifacts(handoff_id,kind,schema_version,sha256,byte_size,raw_bytes) values(?,'CORE','test',?,1,?)",handoff,hash,bytes);
        db.update("insert into analysis_canonical_archive_jobs(handoff_id) values(?)",handoff);
        db.update("insert into analysis_canonical_archive_artifacts(handoff_id,kind,state,object_key,version_id) values(?,'CORE','ARCHIVED','private/archive','external-version')",handoff);
        return new Chain(session,recording,analysis,execution);
    }
    private UUID direct(JdbcTemplate db,long user) {
        UUID job=UUID.randomUUID();String hash="a".repeat(64);
        db.update("insert into direct_analysis_history(job_id,execution_id,claim_sha256,result_sha256,result_json,script_text) values(?,?,?,?,'{}','private original')",job,UUID.randomUUID(),hash,hash);
        db.update("insert into direct_analysis_history_links(user_id,job_id,claim_sha256) values(?,?,?)",user,job,hash);return job;
    }
    private long member(JdbcTemplate db) {return db.queryForObject("insert into users(nickname,status,role,created_at,updated_at) values('test','ACTIVE','USER',now(),now()) returning id",Long.class);}
    private long count(JdbcTemplate db,String table,String predicate) {return db.queryForObject("select count(*) from "+table+" where "+predicate,Long.class);}
}
