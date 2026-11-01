package org.example.voice.common.logging;

import org.example.voice.coaching.application.CoachingUsageService;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.common.security.LoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HttpLoggingIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean CoachingUsageService usage;
    @Test void securityRejectionHasRequestIdWithoutChangingAuthenticationBody() throws Exception {
        mvc.perform(get("/api/users/me/coaching-usage").header("Authorization","Bearer PRIVATE_SENTINEL"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(header().string("X-Request-ID",org.hamcrest.Matchers.matchesPattern("[a-f0-9-]{36}")));
        verifyNoInteractions(usage);
    }
    @Test void businessRejectionKeepsItsCodeAndExposesCorrelationHeaderForFrontend() throws Exception {
        when(usage.getUsage(12345L)).thenThrow(new BaseException(ErrorCode.COACHING_QUOTA_EXCEEDED));
        mvc.perform(get("/api/users/me/coaching-usage").with(authentication(
                        UsernamePasswordAuthenticationToken.authenticated(new LoginUser(12345L),null,List.of())))
                        .header("Origin","http://localhost:3000"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COACHING_QUOTA_EXCEEDED"))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().string("Access-Control-Expose-Headers",org.hamcrest.Matchers.containsString("X-Request-ID")));
    }
    @Test void notFoundAndUnexpectedFailureRetainTheirHttpStatuses() throws Exception {
        var principal=authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(12345L),null,List.of()));
        when(usage.getUsage(12345L)).thenThrow(new BaseException(ErrorCode.USER_NOT_FOUND));
        mvc.perform(get("/api/users/me/coaching-usage").with(principal)).andExpect(status().isNotFound()).andExpect(header().exists("X-Request-ID"));
        doThrow(new IllegalStateException("PRIVATE_SENTINEL")).when(usage).getUsage(12345L);
        mvc.perform(get("/api/users/me/coaching-usage").with(principal)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }
}
