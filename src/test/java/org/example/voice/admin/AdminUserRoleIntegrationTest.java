package org.example.voice.admin;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.*;
import org.example.voice.admin.application.AdminUserRoleService;
import org.example.voice.auth.application.TokenService;
import org.example.voice.common.exception.BaseException;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.type.UserRole;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="custom-content.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc
class AdminUserRoleIntegrationTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired AdminUserRoleService service;
    @Autowired TokenService tokens;
    @Autowired MockMvc mvc;
    @MockitoBean org.springframework.cache.CacheManager caches;
    TransactionTemplate tx;
    long actor,target;
    String adminToken,userToken;
    @BeforeEach void setup() {
        var local=new org.springframework.cache.concurrent.ConcurrentMapCacheManager();
        when(caches.getCache(anyString())).thenAnswer(call->local.getCache(call.getArgument(0)));
        tx=new TransactionTemplate(manager);
        tx.executeWithoutResult(status->{
            var admin=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","admin",OffsetDateTime.now());
            admin.changeRole(UserRole.ADMIN,OffsetDateTime.now());em.persist(admin);actor=admin.getId();
            var user=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","member",OffsetDateTime.now());
            em.persist(user);target=user.getId();
            adminToken=tokens.issueSession(admin).accessToken();userToken=tokens.issueSession(user).accessToken();
        });
    }
    private String body(String expected,String role) {
        return "{\"expectedRole\":\""+expected+"\",\"role\":\""+role+"\",\"reason\":\"reviewed\"}";
    }
    @Test void promotesAndRevokesWithAuditAndImmediateDenialForOldAdminToken() throws Exception {
        mvc.perform(patch("/api/admin/users/"+target+"/role").header("Authorization","Bearer "+adminToken)
                .contentType("application/json").content(body("USER","ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.userId").value(target)).andExpect(jsonPath("$.data.role").value("ADMIN"));
        var promotedToken=tx.execute(status->tokens.issueSession(em.find(User.class,target)).accessToken());
        mvc.perform(get("/api/admin/me").header("Authorization","Bearer "+promotedToken)).andExpect(status().isOk());
        service.change(actor,target,UserRole.ADMIN,UserRole.USER,"revoke");
        mvc.perform(get("/api/admin/me").header("Authorization","Bearer "+promotedToken)).andExpect(status().isForbidden());
        tx.executeWithoutResult(status->assertThat(em.createQuery("select count(a) from AdminAuditLog a where a.targetId=:id and a.action='USER_ROLE_CHANGE'",Long.class)
                .setParameter("id",String.valueOf(target)).getSingleResult()).isEqualTo(2));
    }
    @Test void rejectsUnauthenticatedAndOrdinaryUsers() throws Exception {
        mvc.perform(patch("/api/admin/users/"+target+"/role").contentType("application/json").content(body("USER","ADMIN")))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/users/"+actor+"/role").header("Authorization","Bearer "+userToken)
                .contentType("application/json").content(body("ADMIN","USER"))).andExpect(status().isForbidden());
    }
    @Test void rejectsSelfChangeStaleRoleAndInvalidRole() throws Exception {
        mvc.perform(patch("/api/admin/users/"+actor+"/role").header("Authorization","Bearer "+adminToken)
                .contentType("application/json").content(body("ADMIN","USER"))).andExpect(status().isConflict());
        mvc.perform(patch("/api/admin/users/"+target+"/role").header("Authorization","Bearer "+adminToken)
                .contentType("application/json").content(body("ADMIN","USER"))).andExpect(status().isConflict());
        mvc.perform(patch("/api/admin/users/"+target+"/role").header("Authorization","Bearer "+adminToken)
                .contentType("application/json").content(body("USER","OWNER"))).andExpect(status().isBadRequest());
    }
    @Test void concurrentMutualRevocationLeavesOneAdministrator() throws Exception {
        service.change(actor,target,UserRole.USER,UserRole.ADMIN,"promote");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var first=pool.submit(()->revoke(start,actor,target));
            var second=pool.submit(()->revoke(start,target,actor));start.countDown();
            assertThat(List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
        }
        tx.executeWithoutResult(status->assertThat(em.createQuery("select count(u) from User u where u.id in (:a,:b) and u.role=:role",Long.class)
                .setParameter("a",actor).setParameter("b",target).setParameter("role",UserRole.ADMIN).getSingleResult()).isEqualTo(1));
    }
    @Test void rejectsInactiveDeletedAndMissingMembersAndBlankReason() throws Exception {
        mvc.perform(patch("/api/admin/users/"+target+"/role").header("Authorization","Bearer "+adminToken)
                .contentType("application/json").content(body("USER","ADMIN").replace("reviewed"," ")))
                .andExpect(status().isBadRequest());
        tx.executeWithoutResult(status->em.createQuery("update User u set u.status=:status where u.id=:id")
                .setParameter("status",org.example.voice.user.domain.type.UserStatus.SUSPENDED).setParameter("id",target).executeUpdate());
        mvc.perform(patch("/api/admin/users/"+target+"/role").header("Authorization","Bearer "+adminToken)
                .contentType("application/json").content(body("USER","ADMIN"))).andExpect(status().isConflict());
        tx.executeWithoutResult(status->{em.find(User.class,target).withdraw(OffsetDateTime.now());});
        mvc.perform(patch("/api/admin/users/"+target+"/role").header("Authorization","Bearer "+adminToken)
                .contentType("application/json").content(body("USER","ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/admin/users/9223372036854775807/role").header("Authorization","Bearer "+adminToken)
                .contentType("application/json").content(body("USER","ADMIN"))).andExpect(status().isNotFound());
    }
    private boolean revoke(java.util.concurrent.CountDownLatch start,long a,long b) throws Exception {
        start.await();try { service.change(a,b,UserRole.ADMIN,UserRole.USER,"revoke");return true; }
        catch(BaseException denied){return false;}
    }
}
