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
            flyway=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("64").load();
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
            var service=new AdminCommunicationService(new AdminAccessService(admin),comm,admin,clock);
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
            tx.executeWithoutResult(s->{
                assertThat(members.activeMember(target)).isTrue();
                assertThat(members.records(target,AdminUserResource.ONBOARDING,0,20).items()).singleElement().satisfies(r->assertThat(r.fields().get("goalText")).isEqualTo("target-goal"));
                assertThat(members.records(other,AdminUserResource.ONBOARDING,0,20).items()).singleElement().satisfies(r->assertThat(r.fields().get("goalText")).isEqualTo("other-goal"));
                assertThat(members.records(target,AdminUserResource.PROFILE_IMAGES,0,20).items()).singleElement().satisfies(r->assertThat(r.fields()).containsEntry("sizeBytes",1024L).doesNotContainKeys("objectKey","imageUrl","originalFileName","requestDigest"));
                assertThat(members.records(target,AdminUserResource.MEMBERSHIP_GRANTS,0,20).totalElements()).isEqualTo(1);

                assertThat(members.resolve(request)).anySatisfy(m->{assertThat(m.userId()).isEqualTo(target);assertThat(m.source()).isEqualTo("ANALYSIS");});
                assertThat(members.resolve(execution)).hasSize(1);assertThat(members.resolve(direct)).hasSize(1);
                assertThat(members.counts(target)).hasSize(AdminUserResource.values().length);
                for(var resource:AdminUserResource.values())members.records(target,resource,0,20);
                var analyses=members.records(target,AdminUserResource.ANALYSES,0,20);
                assertThat(analyses.items().getFirst().fields().get("id")).isEqualTo(analysis);
                assertThat(members.records(other,AdminUserResource.ANALYSES,0,20).items()).isEmpty();
                assertThat(members.records(target,AdminUserResource.RECORDINGS,0,20).items().getFirst().fields()).doesNotContainKey("audioUrl");
            });
            var start=OffsetDateTime.now(clock);
            var popup=tx.execute(s->service.createPopup(actor,new PopupDraft("기간 안내","내용",null,10,start,2,true),"등록"));
            tx.executeWithoutResult(s->{
                assertThat(comm.activePopup(target,start)).isPresent();
                assertThat(comm.activePopup(target,start.minusNanos(1000))).isEmpty();
                assertThat(comm.activePopup(target,start.plusDays(2))).isEmpty();
                assertThat(service.activePopup(target).id()).isEqualTo(popup.id());
            });
            assertThatThrownBy(()->jdbc.update("update admin_popups set ends_at=starts_at where id=?",popup.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            if(factory.getObject()!=null)factory.destroy();
            root.execute("drop schema if exists "+schema+" cascade");
        }
    }
    private long account(JdbcTemplate jdbc,String role) {
        return jdbc.queryForObject("insert into users(nickname,status,role,terms_agreed_at,privacy_agreed_at,created_at,updated_at) values('test','ACTIVE',?,now(),now(),now(),now()) returning id",Long.class,role);
    }
}

