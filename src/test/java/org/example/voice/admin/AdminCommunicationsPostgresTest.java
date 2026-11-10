package org.example.voice.admin;

import jakarta.persistence.*;
import org.example.voice.admin.application.*;
import org.example.voice.admin.domain.model.AdminCommunicationData.*;
import org.example.voice.admin.domain.type.*;
import org.example.voice.admin.infrastructure.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
class AdminCommunicationsPostgresTest {
    @Test void migratedSchemaSupportsMetadataLookupAndAtomicConcurrentDelivery() throws Exception {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL"),user=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres"),password=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD","");
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:");
        String schema="admin_communications_"+UUID.randomUUID().toString().replace("-","");
        var root=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        var factory=new LocalContainerEntityManagerFactoryBean();
        try {
            var flyway=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("63").load();
            flyway.migrate();
            var db=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);
            var jdbc=new JdbcTemplate(db);
            long before=jdbc.queryForObject("select count(*) from users",Long.class);
            flyway=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("72").load();
            flyway.migrate();flyway.validate();
            assertThat(jdbc.queryForObject("select count(*) from users",Long.class)).isEqualTo(before);
            factory.setDataSource(db);
            factory.setManagedTypes(org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes.of(
                    "org.example.voice.user.domain.entity.User",
                    "org.example.voice.admin.domain.entity.AdminAuditLog",
                    "org.example.voice.admin.domain.entity.AdminNotificationCampaign",
                    "org.example.voice.admin.domain.entity.AdminPopup"));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.show_sql","false","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));factory.afterPropertiesSet();
            var emf=Objects.requireNonNull(factory.getObject());
            var em=SharedEntityManagerCreator.createSharedEntityManager(emf);
            var tx=new TransactionTemplate(new JpaTransactionManager(emf));
            var admin=new AdminPersistence(em);var comm=new AdminCommunicationPersistence(em);var members=new AdminMemberPersistence(em);
            var clock=Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"),ZoneOffset.UTC);
            var service=new AdminCommunicationService(new AdminAccessService(admin),comm,admin,clock,org.mockito.Mockito.mock(org.example.voice.admin.application.PopupImageReader.class));
            long actor=account(jdbc,"ADMIN"),target=account(jdbc,"USER"),other=account(jdbc,"USER");
            var c=tx.execute(s->service.create(actor,new CampaignDraft(AdminAudience.SELECTED_USERS,AdminNotificationKind.PLAN,"요금제 안내","서비스 안내","/mypage",List.of(target)),"등록"));
            // A later failure must roll back inbox rows, campaign state and success audit together.
            assertThatThrownBy(()->tx.executeWithoutResult(s->{service.dispatch(actor,c.id(),c.version(),"발송");throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("select count(*) from user_notifications where user_id=?",Long.class,target)).isZero();
            assertThat(jdbc.queryForObject("select sent_at is null from admin_notification_campaigns where id=?",Boolean.class,c.id())).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from admin_audit_logs where action='NOTIFICATION_DISPATCH'",Long.class)).isZero();
            var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(2)) {
                Callable<CampaignView> send=()->{ready.countDown();assertThat(go.await(5,TimeUnit.SECONDS)).isTrue();return tx.execute(s->service.dispatch(actor,c.id(),c.version(),"동시 발송"));};
                var a=pool.submit(send);var b=pool.submit(send);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
                assertThat(a.get(10,TimeUnit.SECONDS).sentCount()).isEqualTo(1);assertThat(b.get(10,TimeUnit.SECONDS).sentCount()).isEqualTo(1);
            }
            assertThat(jdbc.queryForObject("select count(*) from user_notifications where user_id=?",Long.class,target)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from user_notifications where user_id=?",Long.class,other)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from admin_audit_logs where action='NOTIFICATION_DISPATCH'",Long.class)).isEqualTo(1);
            long content=jdbc.queryForObject("select min(id) from practice_contents",Long.class);
            long session=jdbc.queryForObject("insert into training_sessions(user_id,content_id,learning_focus,status,started_at) values(?,?,'PRONUNCIATION','RECORDING',now()) returning id",Long.class,target,content);
            long recording=jdbc.queryForObject("insert into voice_recordings(training_session_id,attempt_no,audio_url,quality_status,created_at) values(?,1,'s3://secret/private.wav','PENDING',now()) returning id",Long.class,session);
            UUID request=UUID.randomUUID(),execution=UUID.randomUUID();
            long analysis=jdbc.queryForObject("insert into analysis_results(recording_id,status,active_request_event_id,active_execution_id,created_at) values(?,'PROCESSING',?,?,now()) returning id",Long.class,recording,request.toString(),execution.toString());
            jdbc.update("insert into onboarding_profiles(user_id,current_level,goal_text,survey_answers,completed_at,updated_at) values(?,'BEGINNER','target-goal','{}',now(),now())",target);
            jdbc.update("insert into onboarding_profiles(user_id,current_level,goal_text,survey_answers,completed_at,updated_at) values(?,'ADVANCED','other-goal','{}',now(),now())",other);
            jdbc.update("insert into profile_images(user_id,object_key,image_url,original_file_name,size_bytes,request_digest,state,created_at,cleanup_after) values(?,?,'https://private.invalid/profile','private-name.png',1024,?,'ACTIVE',now(),now()+interval '1 day')",target,"private/"+target,"b".repeat(64));
            jdbc.update("insert into user_test_membership_grants(user_id,granted_at) values(?,now())",target);
            UUID direct=UUID.randomUUID();
            jdbc.update("insert into direct_analysis_history_links(user_id,job_id,claim_sha256) values(?,?,?)",target,direct,"a".repeat(64));
            var paidUntil=OffsetDateTime.now(clock).plusDays(2);
            jdbc.update("insert into payment_test_orders(id,user_id,idempotency_key,product,amount,currency,status,created_at,paid_at,expires_at) values('bulk-valid',?,'valid','SPEAKAI_MONTHLY_TEST_V1',15000,'KRW','PAID',now(),?,?)",other,OffsetDateTime.now(clock).minusDays(1),paidUntil);
            jdbc.update("insert into payment_test_orders(id,user_id,idempotency_key,product,amount,currency,status,created_at,paid_at,expires_at) values('bulk-future',?,'future','SPEAKAI_MONTHLY_TEST_V1',15000,'KRW','PAID',now(),?,?)",other,OffsetDateTime.now(clock).plusDays(1),paidUntil.plusDays(20));
            jdbc.update("insert into payment_test_orders(id,user_id,idempotency_key,product,amount,currency,status,created_at,paid_at,expires_at) values('bulk-expired',?,'expired','SPEAKAI_MONTHLY_TEST_V1',15000,'KRW','PAID',now(),?,?)",other,OffsetDateTime.now(clock).minusDays(5),OffsetDateTime.now(clock).minusDays(1));
            jdbc.update("insert into payment_test_orders(id,user_id,idempotency_key,product,amount,currency,status,created_at,paid_at,expires_at) values('bulk-cancelled',?,'cancelled','SPEAKAI_MONTHLY_TEST_V1',15000,'KRW','CANCELLED',now(),?,?)",other,OffsetDateTime.now(clock).minusDays(1),paidUntil.plusDays(30));
            tx.executeWithoutResult(s->{
                assertThat(members.activeMember(target)).isTrue();
                assertThat(members.records(target,AdminUserResource.ONBOARDING,0,20).items()).singleElement().satisfies(r->assertThat(r.fields().get("goalText")).isEqualTo("target-goal"));
                assertThat(members.records(other,AdminUserResource.ONBOARDING,0,20).items()).singleElement().satisfies(r->assertThat(r.fields().get("goalText")).isEqualTo("other-goal"));
                assertThat(members.records(target,AdminUserResource.PROFILE_IMAGES,0,20).items()).singleElement().satisfies(r->assertThat(r.fields()).containsEntry("sizeBytes",1024L).doesNotContainKeys("objectKey","imageUrl","originalFileName","requestDigest"));
                assertThat(members.records(target,AdminUserResource.MEMBERSHIP_GRANTS,0,20).totalElements()).isEqualTo(1);

                assertThat(members.resolve(request)).anySatisfy(m->{assertThat(m.userId()).isEqualTo(target);assertThat(m.source()).isEqualTo("ANALYSIS");});
                assertThat(members.resolve(execution)).hasSize(1);assertThat(members.resolve(direct)).hasSize(1);
                var statistics=emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();statistics.setStatisticsEnabled(true);
                long beforeCounts=statistics.getPrepareStatementCount();var counts=members.counts(target);
                assertThat(counts).hasSize(AdminUserResource.values().length);
                assertThat(statistics.getPrepareStatementCount()-beforeCounts).isEqualTo(1);
                for(var resource:AdminUserResource.values()) {
                    var page=members.records(target,resource,0,20);
                    assertThat(counts.stream().filter(resourceCount->resourceCount.resource()==resource).findFirst().orElseThrow().count()).isEqualTo(page.totalElements());
                }
                var facts=new org.example.voice.payment.infrastructure.JpaMembershipFactsReader(em);
                long beforeFacts=statistics.getPrepareStatementCount();
                assertThat(facts.readAll(List.of(target,other),OffsetDateTime.now(clock))).hasSize(2)
                    .anySatisfy(f->{assertThat(f.userId()).isEqualTo(target);assertThat(f.testGrant()).isTrue();})
                    .anySatisfy(f->{assertThat(f.userId()).isEqualTo(other);assertThat(f.paidUntil().toInstant()).isEqualTo(paidUntil.toInstant());});
                assertThat(statistics.getPrepareStatementCount()-beforeFacts).isEqualTo(1);
                var analyses=members.records(target,AdminUserResource.ANALYSES,0,20);
                assertThat(analyses.items().getFirst().fields().get("id")).isEqualTo(analysis);
                assertThat(members.records(other,AdminUserResource.ANALYSES,0,20).items()).isEmpty();
                assertThat(members.records(target,AdminUserResource.RECORDINGS,0,20).items().getFirst().fields()).doesNotContainKey("audioUrl");
            });
            jdbc.update("update users set status='WITHDRAWN' where id=?",target);
            tx.executeWithoutResult(s->{assertThat(members.visibleMember(target)).isFalse();assertThat(members.resolve(request)).isEmpty();assertThat(members.resolve(execution)).isEmpty();assertThat(members.resolve(direct)).isEmpty();});
            jdbc.update("update users set status='ACTIVE',deleted_at=now() where id=?",target);
            tx.executeWithoutResult(s->{assertThat(members.visibleMember(target)).isFalse();assertThat(members.resolve(request)).isEmpty();assertThat(members.resolve(direct)).isEmpty();});
            jdbc.update("update users set deleted_at=null where id=?",target);
            var start=OffsetDateTime.now(clock);
            var popup=tx.execute(s->service.createPopup(actor,new PopupDraft("기간 안내","내용",null,10,start,2,true),"등록"));
            var firstImage=UUID.randomUUID();var secondImage=UUID.randomUUID();
            jdbc.update("insert into admin_popup_images(id,popup_id,object_key,state,created_at) values(?,?,?,'ATTACHED',now())",firstImage,popup.id(),"popup-images/"+firstImage+".png");
            assertThatThrownBy(()->jdbc.update("insert into admin_popup_images(id,popup_id,object_key,state,created_at) values(?,?,?,'ATTACHED',now())",secondImage,popup.id(),"popup-images/"+secondImage+".png"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            jdbc.update("update admin_popup_images set state='RETIRED' where id=?",firstImage);
            jdbc.update("insert into admin_popup_images(id,popup_id,object_key,state,created_at) values(?,?,?,'ATTACHED',now())",secondImage,popup.id(),"popup-images/"+secondImage+".png");
            tx.executeWithoutResult(s->{
                assertThat(comm.activePopup(target,start)).isPresent();
                assertThat(comm.activePopup(target,start.minusNanos(1000))).isEmpty();
                assertThat(comm.activePopup(target,start.plusDays(2))).isEmpty();
                assertThat(service.activePopup(target).id()).isEqualTo(popup.id());
            });
            assertThatThrownBy(()->jdbc.update("update admin_popups set ends_at=starts_at where id=?",popup.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            concurrentDraftChanges(jdbc,tx,service,actor,other);
        } finally {
            if(factory.getObject()!=null)factory.destroy();
            root.execute("drop schema if exists "+schema+" cascade");
        }
    }
    private void concurrentDraftChanges(JdbcTemplate jdbc,TransactionTemplate tx,AdminCommunicationService service,long actor,long target) throws Exception {
        for(boolean deleting:List.of(false,true)) {
            var original=tx.execute(s->service.create(actor,new CampaignDraft(AdminAudience.SELECTED_USERS,AdminNotificationKind.GENERAL,"원문","내용","/home",List.of(target)),"등록"));
            long before=jdbc.queryForObject("select count(*) from user_notifications where user_id=?",Long.class,target);
            var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(2)) {
                Callable<Boolean> mutate=()->{
                    ready.countDown();assertThat(go.await(5,TimeUnit.SECONDS)).isTrue();
                    try {
                        tx.executeWithoutResult(s->{
                            if(deleting)service.delete(actor,original.id(),original.version(),"초안 삭제");
                            else service.edit(actor,original.id(),original.version(),new CampaignDraft(AdminAudience.SELECTED_USERS,AdminNotificationKind.PLAN,"수정","새 내용","/mypage",List.of(target)),"초안 수정");
                        });return true;
                    } catch(org.example.voice.common.exception.BaseException e) {
                        assertThat(e.getErrorCode()).isEqualTo(org.example.voice.common.exception.ErrorCode.ADMIN_EDIT_CONFLICT);return false;
                    }
                };
                Callable<Boolean> send=()->{
                    ready.countDown();assertThat(go.await(5,TimeUnit.SECONDS)).isTrue();
                    try { tx.executeWithoutResult(s->service.dispatch(actor,original.id(),original.version(),"발송"));return true; }
                    catch(org.example.voice.common.exception.BaseException e) {
                        assertThat(e.getErrorCode()).isIn(org.example.voice.common.exception.ErrorCode.ADMIN_EDIT_CONFLICT,org.example.voice.common.exception.ErrorCode.RESOURCE_NOT_FOUND);return false;
                    }
                };
                var changed=pool.submit(mutate);var sent=pool.submit(send);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
                boolean changeWon=changed.get(15,TimeUnit.SECONDS),sendWon=sent.get(15,TimeUnit.SECONDS);
                assertThat(changeWon).isNotEqualTo(sendWon);
                assertThat(jdbc.queryForObject("select count(*) from user_notifications where user_id=?",Long.class,target)).isEqualTo(before+(sendWon?1:0));
                if(deleting&&changeWon) {
                    assertThat(jdbc.queryForObject("select count(*) from admin_notification_campaigns where id=?",Long.class,original.id())).isZero();
                    assertThat(jdbc.queryForObject("select count(*) from admin_notification_recipients where campaign_id=?",Long.class,original.id())).isZero();
                } else {
                    var remaining=tx.execute(s->service.campaign(actor,original.id()));
                    assertThat(remaining.title()).isEqualTo(sendWon?"원문":"수정");
                    assertThat(remaining.status()).isEqualTo(sendWon?"SENT":"DRAFT");
                }
            }
        }
        var rollback=tx.execute(s->service.create(actor,new CampaignDraft(AdminAudience.SELECTED_USERS,AdminNotificationKind.GENERAL,"보존","내용","/home",List.of(target)),"등록"));
        assertThatThrownBy(()->tx.executeWithoutResult(s->{service.delete(actor,rollback.id(),rollback.version(),"삭제");throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(tx.execute(s->service.campaign(actor,rollback.id())).userIds()).containsExactly(target);
        assertThat(jdbc.queryForObject("select count(*) from admin_audit_logs where action='NOTIFICATION_DELETE' and target_id=?",Long.class,Long.toString(rollback.id()))).isZero();
    }
    private long account(JdbcTemplate jdbc,String role) {
        return jdbc.queryForObject("insert into users(nickname,status,role,terms_agreed_at,privacy_agreed_at,created_at,updated_at) values('test','ACTIVE',?,now(),now(),now(),now()) returning id",Long.class,role);
    }
}

