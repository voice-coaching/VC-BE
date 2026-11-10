package org.example.voice.common.security;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.*;
import org.example.voice.auth.application.TokenService;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.type.UserRole;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="custom-content.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc @Transactional @Import(AdminApiBoundaryIntegrationTest.ProbeController.class)
class AdminApiBoundaryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired TokenService tokens;
    @MockitoBean org.springframework.cache.CacheManager caches;
    User member,admin;
    String userToken,adminToken;
    @BeforeEach void setup() {
        var local=new org.springframework.cache.concurrent.ConcurrentMapCacheManager();
        when(caches.getCache(anyString())).thenAnswer(i->local.getCache(i.getArgument(0)));
        member=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","member",OffsetDateTime.now());em.persist(member);
        admin=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","admin",OffsetDateTime.now());
        admin.changeRole(UserRole.ADMIN,OffsetDateTime.now());em.persist(admin);
        userToken=tokens.issueSession(member).accessToken();adminToken=tokens.issueSession(admin).accessToken();
    }
    @Test void signedUserCannotReadAnyAdminListsOrSubmitRoleChanges() throws Exception {
        for(var path:List.of("/api/admin","/api/admin/me","/api/admin/users","/api/admin/contents","/api/admin/courses",
                "/api/admin/title-exam-policies","/api/admin/notices","/api/admin/inquiries","/api/admin/popups","/api/admin/audit-logs","/api/admin/notification-campaigns"))
            mvc.perform(get(path).header("Authorization","Bearer "+userToken).header("X-User-Id",admin.getId()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(patch("/api/admin/users/"+member.getId()+"/role").header("Authorization","Bearer "+userToken)
                .contentType("application/json").content("{\"role\":\"ADMIN\",\"expectedRole\":\"USER\",\"reason\":\"forged grant\"}"))
                .andExpect(status().isForbidden());
        assertThat(em.find(User.class,member.getId()).getRole()).isEqualTo(UserRole.USER);
    }
    @Test void commonRoleGateProtectsAnEndpointOutsideTheAdminControllerAdvice() throws Exception {
        mvc.perform(get("/api/admin/security-test/probe").header("Authorization","Bearer "+userToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/security-test/probe").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/security-test/probe")).andExpect(status().isUnauthorized());
    }
    @Test void changingTheRoleClaimWithoutResigningDoesNotAuthorizeAccess() throws Exception {
        var parts=userToken.split("\\.");
        var original=new String(Base64.getUrlDecoder().decode(parts[1]),java.nio.charset.StandardCharsets.UTF_8);
        var changed=original.replace("\"USER\"","\"ADMIN\"");assertThat(changed).isNotEqualTo(original);
        var forged=parts[0]+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(changed.getBytes(java.nio.charset.StandardCharsets.UTF_8))+"."+parts[2];
        mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+forged)).andExpect(status().isUnauthorized());
    }
    @Test void validAdminClaimStillRequiresCurrentDatabaseRoleAndOrdinaryPopupRemainsAccessible() throws Exception {
        mvc.perform(get("/api/admin/me").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
        admin.changeRole(UserRole.USER,OffsetDateTime.now());em.flush();
        mvc.perform(get("/api/admin/me").header("Authorization","Bearer "+adminToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/me/popup").header("Authorization","Bearer "+userToken)).andExpect(status().isOk());
    }
    /** Test-only: no administrator module advice applies to this class's package. */
    @RestController
    static class ProbeController {
        @GetMapping("/api/admin/security-test/probe") String probe(){return "protected";}
    }
}
