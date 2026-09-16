package org.example.voice.analysis.controller;

import org.example.voice.analysis.application.AnalysisRunPodCallbackService;
import org.example.voice.analysis.domain.model.AnalysisRunPodControlData;
import org.example.voice.analysis.domain.type.AnalysisResultIngestionDisposition;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.example.voice.analysis.infrastructure.runpod.RunPodContractTest.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

class InternalRunPodAnalysisControllerTest {
    private final RunPodContract contract = new RunPodContract();
    private final AnalysisRunPodCallbackService service = mock(AnalysisRunPodCallbackService.class);
    private final RunPodBackendReadiness readiness = mock(RunPodBackendReadiness.class);
    private MockMvc mvc;
    private final String base = "/api/internal/ai";
    private final String token = "callback-test-token";

    @BeforeEach
    void setup() {
        RunPodAnalysisProperties properties = new RunPodAnalysisProperties();
        properties.setCallbackToken(token);
        var controller = new InternalRunPodAnalysisController(new RunPodInternalAuthentication(properties), service, contract, readiness);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new RunPodExceptionHandler()).build();
    }

    @Test
    void readinessIsAuthenticatedAndUsesExactWorkerSchema() throws Exception {
        var denied = mvc.perform(get(base+"/worker-readiness")).andExpect(status().isUnauthorized()).andReturn();
        contract.parse(denied.getResponse().getContentAsByteArray(), "error");
        verifyNoInteractions(readiness);
        when(readiness.isReady()).thenReturn(true);
        var ready = mvc.perform(get(base+"/worker-readiness").header("Authorization", "Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contractVersion").value(RunPodContract.VERSION)).andReturn();
        contract.parse(ready.getResponse().getContentAsByteArray(), "workerReadiness");
        fixture("workerReadiness", ready.getResponse().getContentAsByteArray());
        when(readiness.isReady()).thenReturn(false);
        mvc.perform(get(base+"/worker-readiness").header("Authorization", "Bearer "+token)).andExpect(status().isServiceUnavailable());
    }

    @Test
    void claimAndHeartbeatReturnFlatLeaseAndResultReturnsCommittedAck() throws Exception {
        var data = new AnalysisRunPodControlData(1L, REQUEST, EXECUTION, WORKER.toString(),
                RunPodContract.timestamp(OffsetDateTime.now()), RunPodContract.timestamp(OffsetDateTime.now().plusSeconds(90)), true);
        when(service.claim(eq(1L), any())).thenReturn(data);
        when(service.heartbeat(eq(1L), any())).thenReturn(data);
        String identities = "\"requestId\":\""+REQUEST+"\",\"executionId\":\""+EXECUTION+"\",\"workerInstanceId\":\""+WORKER+"\"";
        var response = mvc.perform(post(base+"/analyses/1/claim").header("Authorization", "Bearer "+token)
                .contentType("application/json").content("{"+identities+",\"requestPayloadSha256\":\""+"a".repeat(64)+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist()).andReturn();
        contract.parse(response.getResponse().getContentAsByteArray(), "leaseResponse");
        fixture("leaseResponse", response.getResponse().getContentAsByteArray());
        var heartbeat = mvc.perform(post(base+"/analyses/1/heartbeat").header("Authorization", "Bearer "+token)
                .contentType("application/json").content("{"+identities+"}")).andExpect(status().isOk()).andReturn();
        contract.parse(heartbeat.getResponse().getContentAsByteArray(), "leaseResponse");
        when(service.ingestResult(eq(1L), any())).thenReturn(AnalysisResultIngestionDisposition.APPLIED, AnalysisResultIngestionDisposition.IGNORED_DUPLICATE);
        String raw = result(UUID.randomUUID());
        for (String expected : new String[]{"APPLIED", "DUPLICATE"}) {
            var ack = mvc.perform(post(base+"/analyses/1/result").header("Authorization", "Bearer "+token)
                    .contentType("application/json").content(raw)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(expected)).andReturn();
            contract.parse(ack.getResponse().getContentAsByteArray(), "resultAck");
            fixture("resultAck", ack.getResponse().getContentAsByteArray());
        }
    }

    @Test
    void rejectsUnauthenticatedMalformedAndOversizedRequestsBeforeService() throws Exception {
        mvc.perform(post(base+"/analyses/1/claim").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post(base+"/analyses/1/claim").header("Authorization", "Bearer "+token, "Bearer "+token)
                .contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post(base+"/analyses/1/claim").header("Authorization", "Bearer "+token)
                .contentType("text/plain").content("{}")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(base+"/analyses/1/claim").header("Authorization", "Bearer "+token)
                .contentType("application/json").content("{")).andExpect(status().isBadRequest());
        mvc.perform(post(base+"/analyses/1/claim").header("Authorization", "Bearer "+token)
                .contentType("application/json").content("{}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post(base+"/analyses/1/claim").header("Authorization", "Bearer "+token)
                .contentType("application/json").content(new byte[65537])).andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(service);
    }

    @Test
    void databaseFailureIsRetryableAndDoesNotExposeInternalDetails() throws Exception {
        when(service.ingestResult(anyLong(), any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private credentials"));
        var response = mvc.perform(post(base+"/analyses/1/result").header("Authorization", "Bearer "+token)
                .contentType("application/json").content(result(UUID.randomUUID())))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After","1")).andReturn();
        contract.parse(response.getResponse().getContentAsByteArray(), "error");
        fixture("error", response.getResponse().getContentAsByteArray());
        assertThat(response.getResponse().getContentAsString()).doesNotContain("private credentials");
    }

    private static void fixture(String kind, byte[] bytes) throws Exception {
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/runpod-contract-fixtures"));
        java.nio.file.Files.write(java.nio.file.Path.of("build/runpod-contract-fixtures/"+kind+".json"), bytes);
    }
}
