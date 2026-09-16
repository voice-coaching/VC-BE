package org.example.voice.analysis.infrastructure.runpod;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import java.time.OffsetDateTime;
import static org.example.voice.analysis.infrastructure.runpod.RunPodContractTest.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;

class RunPodAnalysisClientTest {
    @Test
    void sendsExactlyPersistedJsonAndAcceptsWorkerIdentity() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var properties = new RunPodAnalysisProperties();
        properties.setEndpointUrl("https://worker.example");
        properties.setApiToken("service-token");
        properties.setCallbackToken("callback-token");
        properties.setCallbackBaseUrl("https://backend.example");
        var codec = new RunPodAnalysisPayloadCodec();
        var request = codec.decodeRequest(payload(OffsetDateTime.now().plusMinutes(5)));
        var persisted = codec.encodeRequest(request);
        server.expect(requestTo("https://worker.example/v1/analysis-jobs"))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andExpect(content().string(persisted))
                .andRespond(withSuccess("{\"requestId\":\""+REQUEST+"\",\"executionId\":\""+EXECUTION
                        +"\",\"workerInstanceId\":\""+WORKER+"\",\"status\":\"RUNNING\"}", MediaType.APPLICATION_JSON));
        assertThat(new RunPodAnalysisClient(properties, builder).submit(request).workerInstanceId()).isEqualTo(WORKER);
        server.verify();
    }
}
