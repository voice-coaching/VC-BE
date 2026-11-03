package org.example.voice.auth.controller;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.example.voice.auth.domain.port.TokenProvider;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.type.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class JwtSignatureIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired ObjectMapper json;
    @Autowired TokenProvider provider;
    @Autowired PasswordEncoder passwords;

    @Test void loginAndRefreshHttpResponsesIssueSignedCredentials() throws Exception {
        var user=member();
        var login=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email",user.getEmail(),"password","Fixture-password-123!"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value(true)).andReturn().getResponse();
        String access=json.readTree(login.getContentAsString()).path("data").path("accessToken").asText();
        String refresh=cookie(login.getHeader("Set-Cookie"));
        assertSigned(access);assertSigned(refresh);
        assertThat(provider.parseAccessToken(access).userId()).isEqualTo(user.getId());
        var renewal=mvc.perform(post("/api/auth/token/refresh").cookie(new Cookie("refreshToken",refresh)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value(true)).andReturn().getResponse();
        assertSigned(json.readTree(renewal.getContentAsString()).path("data").path("accessToken").asText());
        assertSigned(cookie(renewal.getHeader("Set-Cookie")));
    }
    @Test void legacyUnsignedRefreshCookieRequiresANewLogin() throws Exception {
        var issued=provider.issue(member().getId(),UserRole.USER);
        String legacy=new PlainJWT(SignedJWT.parse(issued.refreshToken()).getJWTClaimsSet()).serialize();
        mvc.perform(post("/api/auth/token/refresh").cookie(new Cookie("refreshToken",legacy)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.result").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }
    @Test void tamperedRefreshCookieIsRejectedAtTheHttpBoundary() throws Exception {
        var issued=provider.issue(member().getId(),UserRole.USER);String[] parts=issued.refreshToken().split("\\.");
        var forged=new JWTClaimsSet.Builder(SignedJWT.parse(issued.refreshToken()).getJWTClaimsSet()).claim("role","ADMIN").build();
        String body=Base64.getUrlEncoder().withoutPadding().encodeToString(forged.toString().getBytes(StandardCharsets.UTF_8));
        String altered=parts[0]+"."+body+"."+parts[2];
        mvc.perform(post("/api/auth/token/refresh").cookie(new Cookie("refreshToken",altered)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }
    @Test void missingRefreshCookieIsRejectedByRealTokenParsing() throws Exception {
        mvc.perform(post("/api/auth/token/refresh")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }
    private User member() {
        var user=User.createLocal(UUID.randomUUID()+"@example.invalid",passwords.encode("Fixture-password-123!"),"signature fixture",OffsetDateTime.now(ZoneOffset.UTC));
        em.persist(user);em.flush();return user;
    }
    private void assertSigned(String raw) throws Exception {
        assertThat(SignedJWT.parse(raw).getHeader().getAlgorithm().getName()).isEqualTo("HS256");
        assertThat(raw.split("\\.")).hasSize(3);assertThat(raw.split("\\.")[2]).isNotBlank();
    }
    private String cookie(String header) {
        assertThat(header).startsWith("refreshToken=").contains("HttpOnly");
        return header.substring("refreshToken=".length(),header.indexOf(';'));
    }
}
