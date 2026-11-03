package org.example.voice.admin;

import jakarta.persistence.EntityManager;
import org.example.voice.admin.infrastructure.AdminPersistence;
import org.example.voice.admin.domain.port.AdminMemberDeletionStore;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

@SpringBootTest
@AutoConfigureMockMvc @Transactional
class AdminMemberDeletionIntegrationTest {
    @Autowired MockMvc mvc; @Autowired EntityManager em; @Autowired TokenService tokens;
    @Autowired TokenProvider provider; @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean AdminPersistence audit;
    @MockitoSpyBean org.example.voice.auth.infrastructure.UserSessionRevokerImpl sessions;
    @MockitoBean AdminMemberDeletionStore deletion;
    @MockitoBean org.example.voice.user.application.UserService publicWithdrawal;
    long actor,target; OffsetDateTime updated;
    final Map<Long,OffsetDateTime> receipts=new HashMap<>();
    @BeforeEach void setup() {
        receipts.clear();
        when(deletion.deletedAt(anyLong())).thenAnswer(i->Optional.ofNullable(receipts.get(i.getArgument(0))));
        when(deletion.deleteDatabaseRecords(anyLong())).thenAnswer(i->{
            long id=i.getArgument(0);em.remove(em.find(User.class,id));em.flush();
            var now=OffsetDateTime.now(ZoneOffset.UTC);receipts.put(id,now);return now;
        });
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            var a=User.createLocal(UUID.randomUUID()+"@example.invalid","encoded-test-password","operator",OffsetDateTime.now(ZoneOffset.UTC));
            ReflectionTestUtils.setField(a,"role",UserRole.ADMIN);em.persist(a);
            var t=User.createLocal(UUID.randomUUID()+"@example.invalid","encoded-test-password","member",OffsetDateTime.now(ZoneOffset.UTC));em.persist(t);
            em.flush();em.refresh(a);em.refresh(t);actor=a.getId();target=t.getId();updated=t.getUpdatedAt();
        });
    }
    @Test void deletesTheMemberAndRetriesUsingOnlyTheReceiptWithoutPublicWithdrawal() throws Exception {
        String body=body(target,updated,"회원 요청 확인");
        mvc.perform(delete("/api/admin/users/{id}",target).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.data.userId").value(target)).andExpect(jsonPath("$.data.alreadyDeleted").value(false));
        assertThat(em.find(User.class,target)).isNull();
        mvc.perform(delete("/api/admin/users/{id}",target).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.alreadyDeleted").value(true));
        verify(sessions,times(1)).revokeAll(target);
        verify(deletion,times(1)).deleteDatabaseRecords(target);verifyNoInteractions(publicWithdrawal);
        assertThat(em.createQuery("select count(a) from AdminAuditLog a where a.action='USER_DELETE' and a.targetId=:id",Long.class).setParameter("id",Long.toString(target)).getSingleResult()).isEqualTo(1);
    }
    @Test void authenticationConfirmationFreshnessAndSelfGuardsPrecedeDeletion() throws Exception {
        mvc.perform(delete("/api/admin/users/{id}",target).contentType(MediaType.APPLICATION_JSON).content(body(target,updated,"삭제"))).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/users/{id}",target).with(auth(target,"USER")).contentType(MediaType.APPLICATION_JSON).content(body(target,updated,"삭제"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/users/{id}",target).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body(actor,updated,"삭제"))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/admin/users/{id}",target).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body(target,updated.minusSeconds(1),"삭제"))).andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/users/{id}",actor).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body(actor,updated,"삭제")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADMIN_SELF_DELETION_NOT_ALLOWED"));
        mvc.perform(delete("/api/admin/users/{id}",Long.MAX_VALUE).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body(Long.MAX_VALUE,updated,"삭제"))).andExpect(status().isNotFound());
        verify(deletion,never()).deleteDatabaseRecords(anyLong());verifyNoInteractions(publicWithdrawal);
    }
    @Test void legacyMemberWithoutUpdatedTimestampUsesAnExplicitNullPrecondition() throws Exception {
        em.createQuery("update User u set u.updatedAt=null where u.id=:id").setParameter("id",target).executeUpdate();em.refresh(em.find(User.class,target));
        mvc.perform(delete("/api/admin/users/{id}",target).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body(target,null,"삭제")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.alreadyDeleted").value(false));
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void auditFailureRollsBackDeletion() throws Exception {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test")).when(audit).audit(argThat(a->"USER_DELETE".equals(a.getAction())));
        mvc.perform(delete("/api/admin/users/{id}",target).with(auth(actor,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body(target,updated,"삭제"))).andExpect(status().isServiceUnavailable());
        new TransactionTemplate(manager).executeWithoutResult(tx->{assertThat(em.find(User.class,target).getStatus()).isEqualTo(UserStatus.ACTIVE);});
    }
    private String body(long confirm,OffsetDateTime at,String reason) throws Exception {
        var data=new LinkedHashMap<String,Object>();data.put("confirmationUserId",confirm);data.put("expectedUpdatedAt",at==null?null:at.toString());data.put("reason",reason);
        return new tools.jackson.databind.ObjectMapper().writeValueAsString(data);
    }
    private RequestPostProcessor auth(long id,String role) {
        var issued=tokens.issueSession(em.find(User.class,id));String token=provider.reissue(id,UserRole.valueOf(role),issued.sessionId()).accessToken();
        return request->{request.addHeader("Authorization","Bearer "+token);return request;};
    }
}
