package org.example.voice.analysis.controller;

import org.example.voice.analysis.application.DirectHistoryService;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.common.security.LoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"analysis.direct-history.enabled=true", "analysis.runpod.callback-token=direct-history-test-token"})
@AutoConfigureMockMvc
class DirectHistoryControllerIntegrationTest {
    private static final String INTERNAL = "/api/internal/ai/direct-analysis-history";
    private static final String TOKEN = "Bearer direct-history-test-token";
    @Autowired MockMvc mvc;
    @MockitoBean DirectHistoryService service;

    @Test
    void missingDuplicateAndInvalidAuthenticationReturn401BeforeReadingBody() throws Exception {
        for (String[] headers : new String[][]{{}, {TOKEN, TOKEN}, {"Bearer wrong"}, {"Basic wrong"}}) {
            var request = post(INTERNAL).contentType("text/plain").content("{");
            if (headers.length > 0) request.header("Authorization", (Object[]) headers);
            mvc.perform(request).andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.retryable").value(false))
                    .andExpect(jsonPath("$.reasonCode").isString());
        }
        verifyNoInteractions(service);
    }

    @Test
    void authenticatedInvalidBodiesAreClientErrorsBeforeService() throws Exception {
        mvc.perform(post(INTERNAL).header("Authorization", TOKEN).contentType("text/plain").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(INTERNAL).header("Authorization", TOKEN).contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reasonCode").value("INVALID_JSON"));
        for (String raw : List.of("null", "[]", "")) {
            mvc.perform(post(INTERNAL).header("Authorization", TOKEN).contentType("application/json").content(raw))
                    .andExpect(status().isUnprocessableEntity());
        }
        mvc.perform(post(INTERNAL).header("Authorization", TOKEN).contentType("application/json")
                        .content(new byte[1024 * 1024 + 1]))
                .andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(service);
    }

    @Test
    void protocolErrorsKeepStatusAndDependencyErrorsAreRetryableWithoutPrivateDetails() throws Exception {
        for (int code : new int[]{422, 409}) {
            doThrow(new RunPodContractException(code, "DIRECT_HISTORY_INVALID")).when(service).receive(any());
            mvc.perform(post(INTERNAL).header("Authorization", TOKEN).contentType("application/json").content("{}"))
                    .andExpect(status().is(code)).andExpect(jsonPath("$.retryable").value(false));
        }
        doThrow(new DataAccessResourceFailureException("private database diagnostic")).when(service).receive(any());
        String body = mvc.perform(post(INTERNAL).header("Authorization", TOKEN).contentType("application/json").content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.reasonCode").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryable").value(true)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("private database diagnostic");
    }

    @Test
    void authenticatedDeliveryReturns202AndPassesParsedDocument() throws Exception {
        mvc.perform(post(INTERNAL).header("Authorization", TOKEN).contentType("application/json").content("{\"jobId\":\"fixture\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.state").value("RECEIVED"));
        verify(service).receive(argThat(doc -> doc.path("jobId").asText().equals("fixture")));
    }

    @Test
    void userRoutesRequireJwtAndKeepApiResponseForProtocolAndBusinessErrors() throws Exception {
        var id = UUID.randomUUID();
        mvc.perform(get("/api/direct-analysis-history")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/direct-analysis-history/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/direct-analysis-history/" + id + "/link").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        var user = authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(42L), null, List.of()));
        for (int code : new int[]{422, 409}) {
            doThrow(new RunPodContractException(code, "DIRECT_HISTORY_INVALID")).when(service).link(eq(42L), eq(id), any());
            mvc.perform(post("/api/direct-analysis-history/" + id + "/link").with(user)
                            .contentType("application/json").content("{\"historyClaim\":\"invalid\"}"))
                    .andExpect(status().is(code)).andExpect(jsonPath("$.result").value(false))
                    .andExpect(jsonPath("$.code").value("DIRECT_HISTORY_INVALID"));
        }
        when(service.list(42L)).thenThrow(new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        mvc.perform(get("/api/direct-analysis-history").with(user)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.result").value(false));
    }

    @Test
    void userReadsAndLinksPreserveUserIdentityAndSuccessEnvelope() throws Exception {
        var id = UUID.randomUUID();
        var user = authentication(UsernamePasswordAuthenticationToken.authenticated(new LoginUser(42L), null, List.of()));
        when(service.link(42L, id, "claim")).thenReturn("PENDING");
        when(service.state(42L, id)).thenReturn("SAVED");
        when(service.list(42L)).thenReturn(List.of(Map.of("jobId", id.toString())));
        mvc.perform(post("/api/direct-analysis-history/" + id + "/link").with(user)
                        .contentType("application/json").content("{\"historyClaim\":\"claim\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("PENDING"));
        mvc.perform(get("/api/direct-analysis-history/" + id).with(user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("SAVED"));
        mvc.perform(get("/api/direct-analysis-history").with(user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].jobId").value(id.toString()));
    }

    @Test
    void enabledFeatureExposesOnlyThreeUserRoutesInPublicOpenApi() throws Exception {
        var raw = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var paths = new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw).path("paths");
        paths.fieldNames().forEachRemaining(path -> assertThat(path).doesNotStartWith("/api/internal/"));
        assertThat(paths.path("/api/direct-analysis-history").has("get")).isTrue();
        assertThat(paths.path("/api/direct-analysis-history/{jobId}").has("get")).isTrue();
        assertThat(paths.path("/api/direct-analysis-history/{jobId}/link").has("post")).isTrue();
    }
}
