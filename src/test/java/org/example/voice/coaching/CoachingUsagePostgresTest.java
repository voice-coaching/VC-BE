package org.example.voice.coaching;

import jakarta.persistence.EntityManager;
import org.example.voice.analysis.domain.model.*;
import org.example.voice.analysis.domain.port.AnalysisAuthorizationIssuer;
import org.example.voice.analysis.domain.type.AnalysisExecutionProfile;
import org.example.voice.coaching.application.CoachingUsageService;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.common.security.LoginUser;
import org.example.voice.training.application.TrainingAnalysisSchemaAdmissionService;
import org.example.voice.training.application.TrainingAnalysisRequestService;
import org.example.voice.training.domain.model.AnalysisConsentData;
import org.example.voice.training.domain.port.AnalysisJobPublisher;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
@SpringBootTest(properties={"payment.test.enabled=true","PORTONE_STORE_ID=store","PORTONE_TEST_CHANNEL_KEY=channel",
        "PORTONE_API_SECRET=fixture-secret","PORTONE_WEBHOOK_SECRET=dGVzdC13ZWJob29rLXNpZ25hdHVyZS1rZXk=",
        "analysis.stream.max-concurrent-per-user=50"})
@AutoConfigureMockMvc
class CoachingUsagePostgresTest {
    @Autowired CoachingUsageService usage;
    @Autowired TrainingAnalysisRequestService analysis;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired MockMvc mvc;
    @Autowired org.example.voice.analysis.domain.port.AnalysisResultReader analysisResults;
    @Autowired org.example.voice.analysis.domain.port.AnalysisResultWriter analysisResultWriter;
    @MockitoBean Clock clock;
    @MockitoBean AnalysisJobPublisher publisher;
    @MockitoBean AnalysisAuthorizationIssuer issuer;
    @MockitoBean TrainingAnalysisSchemaAdmissionService schemas;
    final AtomicReference<Instant> now=new AtomicReference<>();
    Long userId, contentId;

    @BeforeEach void setup() {
        now.set(Instant.now().plusSeconds(5).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        when(clock.instant()).thenAnswer(i->now.get());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(schemas.selectProfile(any())).thenReturn(AnalysisExecutionProfile.LEGACY);
        userId=user();
        contentId=jdbc.queryForObject("""
                INSERT INTO practice_contents(content_type,learning_focus,title,script_text,difficulty,status,created_at,updated_at)
                VALUES('SENTENCE','PRONUNCIATION','coaching fixture','연습 문장입니다.','BEGINNER','PUBLISHED',now(),now()) RETURNING id
                """,Long.class);
    }
    @Test void freeEightIncludesReservationsAndNinthApiRequestRollsBackBeforePublication() throws Exception {
        for(int i=0;i<8;i++) { var f=reserve(userId); if(i<7) complete(f,"COACHING_READY"); }
        assertThat(usage.getUsage(userId).used()).isEqualTo(7);
        assertThat(usage.getUsage(userId).reserved()).isEqualTo(1);
        var fresh=fixture(userId,false);
        mvc.perform(post("/api/training-sessions/{id}/analyze",fresh.session()).with(as(userId))
                        .contentType("application/json").content("{\"accepted\":true,\"policyRevision\":\"fixture-v1\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COACHING_QUOTA_EXCEEDED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analysis_results WHERE recording_id=?",Long.class,fresh.recording())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM training_sessions WHERE id=?",String.class,fresh.session())).isEqualTo("RECORDING");
        verifyNoInteractions(publisher,issuer);
        assertThat(usage.getUsage(user()).remaining()).isEqualTo(8);
    }
    @Test void paidThirtyHasIndependentWindowAndCancellationDoesNotResetTrial() {
        var trial=reserve(userId); complete(trial,"COACHING_READY");
        String order=paid(userId);
        assertThat(usage.getUsage(userId).membershipType()).isEqualTo("paid");
        assertThat(usage.getUsage(userId).environment()).isEqualTo("TEST");
        for(int i=0;i<30;i++) complete(reserve(userId),"COMPLETED_NO_ISSUE");
        assertThat(usage.getUsage(userId).used()).isEqualTo(30);
        assertCode(()->reserve(userId),ErrorCode.COACHING_QUOTA_EXCEEDED);
        jdbc.update("UPDATE payment_test_orders SET status='CANCELLED' WHERE id=?",order);
        assertThat(usage.getUsage(userId).membershipType()).isEqualTo("free");
        assertThat(usage.getUsage(userId).remaining()).isEqualTo(7);
        paid(userId);
        assertThat(usage.getUsage(userId).remaining()).isEqualTo(30);
    }
    @Test void trialStartsAtRolloutForExistingAccountsAndAtSignupForNewOnesAndNeverRenews() {
        var rollout=jdbc.queryForObject("SELECT effective_at FROM coaching_usage_policy WHERE id=1",OffsetDateTime.class);
        assertThat(usage.getUsage(userId).startsAt()).isEqualTo(rollout);
        now.set(rollout.plusDays(2).toInstant());
        Long newer=tx().execute(s->{var u=User.createSocial(null,"coaching fixture",OffsetDateTime.now(clock));em.persist(u);em.flush();return u.getId();});
        assertThat(usage.getUsage(newer).startsAt()).isEqualTo(rollout.plusDays(2));
        now.set(rollout.plusMonths(1).minusNanos(1).toInstant());
        assertThat(usage.getUsage(userId).canRequestCoaching()).isTrue();
        now.set(rollout.plusMonths(1).toInstant());
        assertThat(usage.getUsage(userId).remaining()).isZero();
        assertCode(()->reserve(userId),ErrorCode.COACHING_PERIOD_EXPIRED);
        assertThat(usage.getUsage(newer).canRequestCoaching()).isTrue();
        now.set(rollout.plusMonths(3).toInstant());
        assertThat(usage.getUsage(userId).canRequestCoaching()).isFalse();
    }
    @Test void testAdminHasNoCountLimitButDoesNotGainRoleOrSkipResourceLimits() {
        jdbc.update("INSERT INTO user_test_membership_grants(user_id,granted_at) VALUES(?,now())",userId);
        for(int i=0;i<35;i++) reserve(userId);
        assertThat(usage.getUsage(userId).unlimited()).isTrue();
        assertThat(usage.getUsage(userId).remaining()).isNull();
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id=?",String.class,userId)).isEqualTo("USER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM coaching_usage_reservations WHERE user_id=?",Long.class,userId)).isZero();
        var f=fixture(userId,true);
        assertCode(()->tx().executeWithoutResult(s->usage.reserve(userId,f.analysis(),f.recording(),f.request(),"가".repeat(301),1000)),
                ErrorCode.COACHING_SCRIPT_LIMIT_EXCEEDED);
    }
    @Test void failuresTimeoutsAndNonCoachingResultsReleaseAndDuplicateCompletionDoesNotDoubleCharge() {
        var failed=reserve(userId);
        jdbc.update("UPDATE analysis_results SET status='FAILED',failure_code='analysis_execution_timeout' WHERE id=?",failed.analysis());
        assertThat(usage.getUsage(userId).remaining()).isEqualTo(8);
        for(String outcome:List.of("RERECORD_REQUIRED","UNCERTAIN","FAILED_CLOSED")) complete(reserve(userId),outcome);
        assertThat(usage.getUsage(userId).used()).isZero();
        var success=reserve(userId); complete(success,"COACHING_READY"); complete(success,"COACHING_READY");
        assertThat(usage.getUsage(userId).used()).isEqualTo(1);
        assertThat(usage.getUsage(userId).reserved()).isZero();
    }
    @Test void retryReusesOneReceiptAndRechecksQuotaAndCurrentPeriod() {
        var failed=reserve(userId);
        jdbc.update("UPDATE analysis_results SET status='FAILED' WHERE id=?",failed.analysis());
        UUID retry=UUID.randomUUID();
        tx().executeWithoutResult(s->{
            jdbc.update("UPDATE analysis_results SET status='PENDING',active_request_event_id=? WHERE id=?",retry.toString(),failed.analysis());
            usage.reserve(userId,failed.analysis(),failed.recording(),retry,"연습",1000);
        });
        assertThat(usage.getUsage(userId).reserved()).isEqualTo(1);
        complete(failed,"COACHING_READY");
        assertThat(usage.getUsage(userId).used()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM coaching_usage_reservations WHERE analysis_id=?",Long.class,failed.analysis())).isEqualTo(1);
    }
    @Test void concurrentRequestsCannotReserveMoreThanEightAndRollbackDoesNotSpendQuota() throws Exception {
        var pool=Executors.newFixedThreadPool(6);
        try {
            var tasks=new ArrayList<Future<Boolean>>();
            for(int i=0;i<14;i++) tasks.add(pool.submit(()->{
                try {reserve(userId);return true;} catch(BaseException e) {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COACHING_QUOTA_EXCEEDED);return false;
                }
            }));
            int accepted=0; for(var task:tasks) if(task.get(30,TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(8);
        } finally { pool.shutdownNow(); }
        assertThat(usage.getUsage(userId).reserved()).isEqualTo(8);
        Long other=user();
        tx().executeWithoutResult(s->{var f=fixture(other,true);usage.reserve(other,f.analysis(),f.recording(),f.request(),"연습",1000);s.setRollbackOnly();});
        assertThat(usage.getUsage(other).remaining()).isEqualTo(8);
    }
    @Test void deletingHistoryDoesNotRefundCompletedCoachingButReleasesPendingReservation() {
        var success=reserve(userId);complete(success,"COACHING_READY");
        var pending=reserve(userId);
        jdbc.update("DELETE FROM analysis_results WHERE id IN (?,?)",success.analysis(),pending.analysis());
        assertThat(usage.getUsage(userId).used()).isEqualTo(1);
        assertThat(usage.getUsage(userId).reserved()).isZero();
        assertThat(usage.getUsage(userId).remaining()).isEqualTo(7);
    }
    @Test void retryAtFullQuotaRollsBackGenerationAndReadingOldResultsRemainsFreeAfterExpiry() throws Exception {
        var failed=reserve(userId);
        jdbc.update("UPDATE analysis_results SET status='FAILED' WHERE id=?",failed.analysis());
        for(int i=0;i<8;i++) complete(reserve(userId),"COACHING_READY");
        mvc.perform(post("/api/training-sessions/{id}/analysis/retry",failed.session()).with(as(userId))
                        .contentType("application/json").content("{\"accepted\":true,\"policyRevision\":\"fixture-v1\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COACHING_QUOTA_EXCEEDED"));
        assertThat(jdbc.queryForObject("SELECT active_request_event_id FROM analysis_results WHERE id=?",String.class,failed.analysis()))
                .isEqualTo(failed.request().toString());
        assertThat(jdbc.queryForObject("SELECT retry_count FROM analysis_results WHERE id=?",Integer.class,failed.analysis())).isZero();
        now.set(usage.getUsage(userId).expiresAt().plusDays(1).toInstant());
        mvc.perform(get("/api/training-sessions/{id}/analysis/status",failed.session()).with(as(userId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("FAILED"));
        verifyNoInteractions(publisher,issuer);
    }
    @Test void apiRejectsOverlongScriptAndAudioWithoutUsingQuota() throws Exception {
        var longScript=fixture(userId,false);
        jdbc.update("UPDATE practice_contents SET script_text=? WHERE id=?","가".repeat(301),contentId);
        mvc.perform(post("/api/training-sessions/{id}/analyze",longScript.session()).with(as(userId))
                        .contentType("application/json").content("{\"accepted\":true,\"policyRevision\":\"fixture-v1\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("COACHING_SCRIPT_LIMIT_EXCEEDED"));
        jdbc.update("UPDATE practice_contents SET script_text='연습' WHERE id=?",contentId);
        var longAudio=fixture(userId,false);
        jdbc.update("UPDATE voice_recordings SET duration_ms=90001 WHERE id=?",longAudio.recording());
        mvc.perform(post("/api/training-sessions/{id}/analyze",longAudio.session()).with(as(userId))
                        .contentType("application/json").content("{\"accepted\":true,\"policyRevision\":\"fixture-v1\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("COACHING_AUDIO_LIMIT_EXCEEDED"));
        assertThat(usage.getUsage(userId).remaining()).isEqualTo(8);
        verifyNoInteractions(publisher,issuer);
    }
    @Test void usageEndpointIsAuthenticatedOwnerScopedAndNotCached() throws Exception {
        complete(reserve(userId),"COACHING_READY");
        mvc.perform(get("/api/users/me/coaching-usage")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me/coaching-usage").with(as(userId)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.limit").value(8)).andExpect(jsonPath("$.data.used").value(1))
                .andExpect(jsonPath("$.data.remaining").value(7));
        mvc.perform(get("/api/users/me/coaching-usage").with(as(user())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.used").value(0));
    }

    @Test void successfulApiAdmissionAndRetryShareOneReceiptAndPublisherFailureRollsBack() throws Exception {
        allowAuthorization();
        var fresh=fixture(userId,false);
        mvc.perform(post("/api/training-sessions/{id}/analyze",fresh.session()).with(as(userId))
                        .contentType("application/json").content("{\"accepted\":true,\"policyRevision\":\"fixture-v1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        Long id=jdbc.queryForObject("SELECT id FROM analysis_results WHERE recording_id=?",Long.class,fresh.recording());
        assertThat(usage.getUsage(userId).reserved()).isEqualTo(1);
        verify(publisher).publish(any(AnalysisWorkerRequest.class));
        jdbc.update("UPDATE analysis_results SET status='FAILED',failure_code='worker_failure' WHERE id=?",id);
        assertThat(usage.getUsage(userId).remaining()).isEqualTo(8);
        mvc.perform(post("/api/training-sessions/{id}/analysis/retry",fresh.session()).with(as(userId))
                        .contentType("application/json").content("{\"accepted\":true,\"policyRevision\":\"fixture-v1\"}"))
                .andExpect(status().isOk());
        assertThat(usage.getUsage(userId).reserved()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM coaching_usage_reservations WHERE analysis_id=?",Long.class,id)).isEqualTo(1);
        var rollback=fixture(userId,false);
        doThrow(new IllegalStateException("publisher fixture failure")).when(publisher).publish(any(AnalysisWorkerRequest.class));
        assertThatThrownBy(()->analysis.requestAnalysis(rollback.session(),userId,new AnalysisConsentData(true,"fixture-v1")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(usage.getUsage(userId).reserved()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analysis_results WHERE recording_id=?",Long.class,rollback.recording())).isZero();
    }

    @Test void canonicalCommitDeliveryFlagSettlesWithoutReadingDiagnosticJson() {
        var delivered=reserve(userId);
        jdbc.update("UPDATE analysis_results SET status='COMPLETED',canonical_result_event_id=?,coaching_delivered=true WHERE id=?",
                UUID.randomUUID(),delivered.analysis());
        var declined=reserve(userId);
        jdbc.update("UPDATE analysis_results SET status='COMPLETED',canonical_result_event_id=?,coaching_delivered=false WHERE id=?",
                UUID.randomUUID(),declined.analysis());
        assertThat(usage.getUsage(userId).used()).isEqualTo(1);
        assertThat(usage.getUsage(userId).reserved()).isZero();
        assertThat(usage.getUsage(userId).remaining()).isEqualTo(7);
    }

    @Test void verifiedCanonicalCommitChargesDeliveredCoachingAndRefundsDeclinedCoaching() {
        for(boolean delivers:new boolean[]{true,false}) {
            var f=reserve(userId);
            UUID execution=UUID.randomUUID(),event=UUID.randomUUID();
            jdbc.update("""
                    UPDATE analysis_results SET analysis_profile='CANONICAL_AUDIOVISUAL_20261007_V6',
                    expected_result_schema_version='voice-coaching.runpod-analysis-result.v6',active_execution_id=? WHERE id=?
                    """,execution.toString(),f.analysis());
            var doc=mock(org.example.voice.analysis.infrastructure.canonical.CanonicalCallbackDocument.class);
            when(doc.identity()).thenReturn(new org.example.voice.analysis.infrastructure.canonical.CanonicalCallbackDocument.Identity(
                    event,f.request(),execution,UUID.randomUUID(),f.analysis(),f.recording(),contentId));
            when(doc.status()).thenReturn(org.example.voice.analysis.domain.type.AnalysisStatus.COMPLETED);
            when(doc.schemaVersion()).thenReturn("voice-coaching.runpod-analysis-result.v6");
            when(doc.payloadSha256()).thenReturn("b".repeat(64));
            when(doc.source()).thenReturn(new org.example.voice.analysis.infrastructure.canonical.CanonicalCallbackDocument.Source(
                    "c".repeat(64),"a".repeat(64),null,null,null,null,null,null,null));
            when(doc.decision()).thenReturn(new org.example.voice.analysis.infrastructure.canonical.CanonicalCallbackDocument.Decision(
                    delivers?org.example.voice.analysis.infrastructure.canonical.CanonicalCallbackDocument.DecisionStatus.ACCEPT
                            :org.example.voice.analysis.infrastructure.canonical.CanonicalCallbackDocument.DecisionStatus.REJECT,null,null));
            when(doc.adapterStatus()).thenReturn(delivers?"READY":"NOT_APPLICABLE");
            when(doc.feedbackDeliveryAllowed()).thenReturn(delivers);
            when(doc.feedback()).thenReturn(delivers?"연습 피드백":null);
            var controlJdbc=mock(JdbcTemplate.class);
            when(controlJdbc.queryForObject(anyString(),eq(Boolean.class),any())).thenReturn(false);
            var committer=new org.example.voice.analysis.application.CanonicalCallbackCommitter(
                    mock(org.example.voice.analysis.infrastructure.canonical.CanonicalCallbackInbox.class),
                    analysisResults,analysisResultWriter,controlJdbc,
                    mock(org.example.voice.analysis.infrastructure.canonical.CanonicalBackendJournal.class),
                    mock(org.example.voice.analysis.infrastructure.canonical.CanonicalExecutionMediaStore.class));
            tx().executeWithoutResult(s->{committer.commit(doc);em.flush();});
        }
        assertThat(usage.getUsage(userId).used()).isEqualTo(1);
        assertThat(usage.getUsage(userId).reserved()).isZero();
    }


    private void allowAuthorization() {
        when(issuer.issue(any())).thenAnswer(i->{
            AnalysisAuthorizationIssue a=i.getArgument(0);
            var issued=now.get();
            return new AnalysisAuthorizationGrant(AnalysisAuthorizationGrant.LEGACY_GRANT_VERSION,"fixture-key",
                    a.requestEventId(),a.analysisId(),a.contentId(),a.promptRevision(),a.scriptSha256(),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(a.audioObjectKey().getBytes(StandardCharsets.UTF_8))),
                    a.audioSha256(),a.mimeType(),a.fileSizeBytes(),a.durationMs(),a.learningFocus(),a.consentReceiptSha256(),
                    a.consentPolicyRevision(),null,null,null,null,null,null,null,issued,issued.plusSeconds(300),
                    AnalysisAuthorizationGrant.PURPOSE,AnalysisAuthorizationGrant.DATA_CATEGORY,true,false,"b".repeat(64));
        });
    }

    private Fixture reserve(Long owner) {return tx().execute(s->{var f=fixture(owner,true);usage.reserve(owner,f.analysis(),f.recording(),f.request(),"연습",1000);return f;});}
    private void complete(Fixture f,String outcome) {jdbc.update("UPDATE analysis_results SET status='COMPLETED',analysis_outcome=? WHERE id=?",outcome,f.analysis());}
    private Fixture fixture(Long owner,boolean withAnalysis) {
        Long session=jdbc.queryForObject("INSERT INTO training_sessions(user_id,content_id,learning_focus,status,started_at) VALUES(?,?,'PRONUNCIATION',?,now()) RETURNING id",Long.class,owner,contentId,withAnalysis?"ANALYZING":"RECORDING");
        Long recording=jdbc.queryForObject("""
                INSERT INTO voice_recordings(training_session_id,attempt_no,audio_url,mime_type,file_size_bytes,duration_ms,quality_status,is_selected,created_at,audio_sha256)
                VALUES(?,1,'recordings/coaching-fixture.wav','audio/wav',1000,1000,'PASS',true,now(),?) RETURNING id
                """,Long.class,session,"a".repeat(64));
        UUID request=UUID.randomUUID();
        Long analysis=withAnalysis?jdbc.queryForObject("INSERT INTO analysis_results(recording_id,status,created_at,active_request_event_id) VALUES(?,'PENDING',now(),?) RETURNING id",Long.class,recording,request.toString()):null;
        return new Fixture(session,recording,analysis,request);
    }
    private String paid(Long owner) {
        String id="coaching-"+UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payment_test_orders(id,user_id,idempotency_key,product,amount,currency,status,created_at,paid_at,expires_at)
                VALUES(?,?,?,'SPEAKAI_MONTHLY_TEST_V1',15000,'KRW','PAID',?,?,?)
                """,id,owner,id,OffsetDateTime.now(clock),OffsetDateTime.now(clock),OffsetDateTime.now(clock).plusMonths(1));
        return id;
    }
    private Long user() {return tx().execute(s->{var u=User.createSocial(null,"coaching fixture",OffsetDateTime.now(clock).minusDays(2));em.persist(u);em.flush();return u.getId();});}
    private TransactionTemplate tx() {return new TransactionTemplate(manager);}
    private RequestPostProcessor as(Long id) {return authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(id),null,List.of()));}
    private void assertCode(Runnable action,ErrorCode expected) {assertThatThrownBy(action::run).isInstanceOfSatisfying(BaseException.class,e->assertThat(e.getErrorCode()).isEqualTo(expected));}
    private record Fixture(Long session,Long recording,Long analysis,UUID request) {}
}
