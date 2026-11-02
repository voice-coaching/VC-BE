package org.example.voice.admin;

import jakarta.persistence.EntityManager;
import org.example.voice.admin.infrastructure.AdminPersistence;
import org.example.voice.user.application.UserService;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.port.*;
import org.example.voice.user.domain.type.*;
import org.example.voice.auth.application.TokenService;
import org.example.voice.auth.domain.port.TokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="custom-content.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc @Transactional
class AdminMemberWithdrawalIntegrationTest {
    @Autowired MockMvc mvc; @Autowired EntityManager em; @Autowired TokenService tokens;
    @Autowired TokenProvider provider; @Autowired Cleanup cleanup; @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean AdminPersistence audit;
    long actor,target; OffsetDateTime updated;
    @BeforeEach void setup() {
        reset(cleanup.identities,cleanup.sessions,cleanup.analysis,cleanup.consents,cleanup.recordings,cleanup.intents,cleanup.images,cleanup.personal,cleanup.notifications);
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            var a=User.createLocal(UUID.randomUUID()+"@example.invalid","encoded-test-password","operator",OffsetDateTime.now(ZoneOffset.UTC));
            ReflectionTestUtils.setField(a,"role",UserRole.ADMIN);em.persist(a);
            var t=User.createLocal(UUID.randomUUID()+"@example.invalid","encoded-test-password","member",OffsetDateTime.now(ZoneOffset.UTC));em.persist(t);
            em.flush();em.refresh(a);em.refresh(t);actor=a.getId();target=t.getId();updated=t.getUpdatedAt();
        });
    }
    @Test void withdrawalAnonymizesTheMemberAndRunsExistingCleanupOnlyOnce() throws Exception {
        String body=body(target,updated,"관리자 처리");
        mvc.perform(post("/api/admin/users/{id}/withdrawal",target).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.userId").value(target)).andExpect(jsonPath("$.data.alreadyWithdrawn").value(false));
        User user=em.find(User.class,target);
        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);assertThat(user.getEmail()).isNull();assertThat(user.getPassword()).isNull();
        assertThat(user.getNickname()).isEqualTo("탈퇴한 회원");assertThat(user.getDeletedAt()).isNotNull();
        verify(cleanup.identities).revokeForUser(target);verify(cleanup.sessions).revokeAll(target);verify(cleanup.analysis).cancelForUser(target);
        verify(cleanup.consents).revokeForUser(target);verify(cleanup.recordings).scheduleAllForUser(target,org.example.voice.training.domain.type.RecordingDeletionReason.USER_WITHDRAWN);
        verify(cleanup.intents).expireForUser(target);verify(cleanup.images).removeForUser(target);verify(cleanup.personal).eraseForUser(target);verify(cleanup.notifications).eraseForUser(target);
        mvc.perform(post("/api/admin/users/{id}/withdrawal",target).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.alreadyWithdrawn").value(true));
        verify(cleanup.sessions,times(1)).revokeAll(target);
        assertThat(em.createQuery("select count(a) from AdminAuditLog a where a.action='USER_WITHDRAW' and a.targetId=:id",Long.class).setParameter("id",Long.toString(target)).getSingleResult()).isEqualTo(1);
    }
    @Test void authorizationTargetConfirmationFreshnessAndSelfGuardsPrecedeCleanup() throws Exception {
        mvc.perform(post("/api/admin/users/{id}/withdrawal",target).contentType(MediaType.APPLICATION_JSON).content(body(target,updated,"탈퇴"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/users/{id}/withdrawal",target).with(auth(target,"USER")).contentType(MediaType.APPLICATION_JSON).content(body(target,updated,"탈퇴"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users/{id}/withdrawal",target).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body(actor,updated,"탈퇴"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/users/{id}/withdrawal",target).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body(target,updated.minusSeconds(1),"탈퇴"))).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/users/{id}/withdrawal",actor).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body(actor,updated,"탈퇴")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADMIN_SELF_WITHDRAWAL_NOT_ALLOWED"));
        mvc.perform(post("/api/admin/users/{id}/withdrawal",Long.MAX_VALUE).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body(Long.MAX_VALUE,updated,"탈퇴"))).andExpect(status().isNotFound());
        verifyNoInteractions(cleanup.sessions,cleanup.identities,cleanup.personal);
    }
    @Test void legacyMemberWithoutUpdatedTimestampUsesAnExplicitNullPrecondition() throws Exception {
        em.createQuery("update User u set u.updatedAt=null where u.id=:id").setParameter("id",target).executeUpdate();
        em.refresh(em.find(User.class,target));
        mvc.perform(post("/api/admin/users/{id}/withdrawal",target).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body(target,null,"탈퇴")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.alreadyWithdrawn").value(false));
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void auditFailureRollsBackTheAccountChange() throws Exception {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test")).when(audit).audit(argThat(a->"USER_WITHDRAW".equals(a.getAction())));
        mvc.perform(post("/api/admin/users/{id}/withdrawal",target).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body(target,updated,"탈퇴"))).andExpect(status().isServiceUnavailable());
        new TransactionTemplate(manager).executeWithoutResult(tx->{var user=em.find(User.class,target);assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);assertThat(user.getEmail()).isNotNull();assertThat(user.getDeletedAt()).isNull();});
    }
    private String body(long confirm,OffsetDateTime at,String reason) throws Exception {
        var data=new LinkedHashMap<String,Object>();data.put("confirmationUserId",confirm);
        data.put("expectedUpdatedAt",at==null?null:at.toString());data.put("reason",reason);
        return new tools.jackson.databind.ObjectMapper().writeValueAsString(data);
    }
    private RequestPostProcessor auth(long id,String role) {
        var issued=tokens.issueSession(em.find(User.class,id));String token=provider.reissue(id,UserRole.valueOf(role),issued.sessionId()).accessToken();
        return request->{request.addHeader("Authorization","Bearer "+token);return request;};
    }
    static class Cleanup {
        final LoginIdentityRevoker identities=mock(LoginIdentityRevoker.class);final UserSessionRevoker sessions=mock(UserSessionRevoker.class);
        final org.example.voice.analysis.domain.port.AnalysisCancellation analysis=mock(org.example.voice.analysis.domain.port.AnalysisCancellation.class);
        final org.example.voice.consent.domain.port.ProcessingConsentLedger consents=mock(org.example.voice.consent.domain.port.ProcessingConsentLedger.class);
        final org.example.voice.training.domain.port.RecordingDeletionScheduler recordings=mock(org.example.voice.training.domain.port.RecordingDeletionScheduler.class);
        final org.example.voice.training.domain.port.RecordingUploadIntentRegistry intents=mock(org.example.voice.training.domain.port.RecordingUploadIntentRegistry.class);
        final org.example.voice.profileimage.domain.port.ProfileImageLifecycle images=mock(org.example.voice.profileimage.domain.port.ProfileImageLifecycle.class);
        final org.example.voice.practicecontent.domain.port.CustomContentLifecycle personal=mock(org.example.voice.practicecontent.domain.port.CustomContentLifecycle.class);
        final org.example.voice.notification.domain.port.NotificationLifecycle notifications=mock(org.example.voice.notification.domain.port.NotificationLifecycle.class);
    }
    @TestConfiguration static class Configuration {
        @Bean Cleanup cleanup() { return new Cleanup(); }
        @Bean @Primary UserService withdrawalTestService(UserReader reader,UserWriter writer,Cleanup c) {
            return new UserService(reader,writer,mock(LoginProviderReader.class),c.identities,c.sessions,mock(org.example.voice.onboarding.domain.port.OnboardingProfileReader.class),
                    c.consents,c.recordings,c.intents,c.analysis,c.images,c.personal,c.notifications,id->org.example.voice.payment.domain.Membership.free());
        }
    }
}
