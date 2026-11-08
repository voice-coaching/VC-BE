package org.example.voice.common.security;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.example.voice.auth.application.TokenService;
import org.example.voice.auth.domain.model.IssuedTokens;
import org.example.voice.analysis.infrastructure.runpod.RunPodBackendReadiness;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"analysis.runpod.callback-token=boundary-service-token"})
@AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="VC_BE_TEST_REDIS_HOST",matches=".+")
class AuthenticationBoundaryIntegrationTest {
    @DynamicPropertySource static void redis(DynamicPropertyRegistry r){
        r.add("spring.data.redis.host",()->System.getenv("VC_BE_TEST_REDIS_HOST"));
        r.add("spring.data.redis.port",()->System.getenv("VC_BE_TEST_REDIS_PORT"));
        r.add("spring.data.redis.ssl.enabled",()->true);
    }
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @MockitoBean RunPodBackendReadiness readiness;
    User user;

    @BeforeEach void setup(){
        user=User.createLocal(UUID.randomUUID()+"@example.invalid",passwords.encode("Fixture-password-123!"),"boundary",OffsetDateTime.now());
        em.persist(user);em.flush();
    }

    @Test void missingInvalidAndForgedIdentityCannotReachProtectedApis() throws Exception {
        for(String path:List.of("/api/users/me","/api/home","/api/courses","/api/practice-contents",
                "/api/practice-contents/facets?type=NEWS","/api/payment-orders/order","/api/admin/me")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            mvc.perform(get(path).header("X-User-Id",user.getId())).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        }
    }

    @Test void signedActiveTokenSuppliesTheVerifiedUserAndRejectsAlteredAndRefreshTokens() throws Exception {
        IssuedTokens issued=tokens.issueSession(user);
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+issued.accessToken()).header("X-User-Id",user.getId()+100000))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(user.getId()));
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+issued.accessToken()+"invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+issued.refreshToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test void replacingAndRevokingSessionsInvalidatePreviouslySignedAccess() throws Exception {
        var old=tokens.issueSession(user);var current=tokens.issueSession(user);
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+old.accessToken())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+current.accessToken())).andExpect(status().isOk());
        tokens.revoke(user.getId(),current.refreshToken());
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+current.accessToken())).andExpect(status().isUnauthorized());
    }

    @Test void loginRefreshAndLogoutUseRealCredentialsAndPreserveInflightAccess() throws Exception {
        String body=json.writeValueAsString(Map.of("email",user.getEmail(),"password","Fixture-password-123!"));
        var login=mvc.perform(post("/api/auth/login").header("Authorization","Bearer stale")
                .contentType("application/json").content(body)).andExpect(status().isOk()).andReturn().getResponse();
        String access=json.readTree(login.getContentAsString()).path("data").path("accessToken").asText();
        String refresh=cookie(login.getHeader("Set-Cookie"));
        var renewal=mvc.perform(post("/api/auth/token/refresh").header("Authorization","Bearer stale")
                .cookie(new Cookie("refreshToken",refresh))).andExpect(status().isOk()).andReturn().getResponse();
        String newRefresh=cookie(renewal.getHeader("Set-Cookie"));
        assertThat(newRefresh).isNotEqualTo(refresh);
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+access)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization","Bearer "+access).cookie(new Cookie("refreshToken",newRefresh)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/token/refresh").cookie(new Cookie("refreshToken",newRefresh))).andExpect(status().isUnauthorized());
    }

    @Test void wrongPasswordAndReplayedRefreshAreRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email",user.getEmail(),"password","Wrong-password-123!"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        var issued=tokens.issueSession(user);tokens.rotate(issued.refreshToken());
        mvc.perform(post("/api/auth/token/refresh").cookie(new Cookie("refreshToken",issued.refreshToken())))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test void callbackAuthenticationIsIndependentOfUserJwtAndRejectsMissingWrongAndDuplicateTokens() throws Exception {
        for(String path:List.of("/api/internal/ai/worker-readiness","/api/internal/ai/worker-readiness/handoff",
                "/api/internal/ai/worker-readiness/audiovisual","/api/internal/ai/analyses/1/evidence/00000000-0000-0000-0000-000000000000",
                "/api/internal/ai/analyses/1/handoffs/00000000-0000-0000-0000-000000000000",
                "/api/internal/ai/analyses/1/journal/00000000-0000-0000-0000-000000000000")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization","Bearer wrong")).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization","Bearer boundary-service-token","Bearer boundary-service-token"))
                    .andExpect(status().isUnauthorized());
        }
        when(readiness.isReady()).thenReturn(true);
        mvc.perform(get("/api/internal/ai/worker-readiness").header("Authorization","Bearer boundary-service-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ready"));
        verify(readiness,times(1)).isReady();
        mvc.perform(post("/api/internal/ai/direct-analysis-history").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/ai/archives/00000000-0000-0000-0000-000000000000/artifacts/core/reconciliation")
                .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void publicRoutesStayReachableAndCorsHonorsConfiguredOrigins() throws Exception {
        mvc.perform(get("/api/auth/email-availability").param("email",user.getEmail())).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/api/browser-video/.well-known/upload-grant-keys"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ANALYSIS_INTEGRATION_UNAVAILABLE"));
        mvc.perform(options("/api/users/me").header("Origin","http://localhost:3000").header("Access-Control-Request-Method","GET"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:3000"));
        mvc.perform(options("/api/users/me").header("Origin","https://unconfigured.example").header("Access-Control-Request-Method","GET"))
                .andExpect(status().isForbidden());
    }

    String cookie(String header){return header.substring("refreshToken=".length(),header.indexOf(';'));}
}
