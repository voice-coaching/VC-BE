package org.example.voice.analysis.infrastructure.runpod;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis", name = "transport", havingValue = "runpod_http")
public class RunPodAnalysisClient {

    private final RunPodAnalysisProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final RunPodContract contract = new RunPodContract();
    private final RunPodAnalysisPayloadCodec codec = new RunPodAnalysisPayloadCodec();

    public RunPodAnalysisJobAccepted submit(RunPodAnalysisJobRequest request) {
        if (!properties.isConfigured()) {
            throw new RunPodAnalysisDeliveryException("runpod_analysis_not_configured", false, null);
        }
        try {
            return restClientBuilder.build()
                    .post()
                    .uri(properties.normalizedEndpointUrl() + "/v1/analysis-jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiToken())
                    .body(codec.encodeRequest(request))
                    .exchange((httpRequest, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != 200 && status != 202) {
                            throw new RunPodAnalysisDeliveryException("runpod_http_" + status, status == 429 || status >= 500, null);
                        }
                        var json = contract.parse(response.getBody().readNBytes(RunPodContract.CONTROL_LIMIT + 1), "jobAccepted");
                        return contract.convert(json, RunPodAnalysisJobAccepted.class);
                    });
        } catch (RunPodAnalysisDeliveryException error) {
            throw error;
        } catch (RestClientException error) {
            throw new RunPodAnalysisDeliveryException("runpod_http_io_error", true, error);
        }
    }

    public void cancel(UUID requestId, UUID executionId) {
        if (!properties.isConfigured()) {
            throw new RunPodAnalysisDeliveryException("runpod_analysis_not_configured", false, null);
        }
        try {
            restClientBuilder.build()
                    .post()
                    .uri(properties.normalizedEndpointUrl() + "/v1/analysis-jobs/" + requestId + "/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiToken())
                    .body(new RunPodAnalysisCancelRequest(executionId))
                    .exchange((httpRequest, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != 200 && status != 202) {
                            if (status == 404 || status == 409) {
                                var error = contract.parse(response.getBody().readNBytes(RunPodContract.CONTROL_LIMIT + 1), "error");
                                if (java.util.Set.of("UNKNOWN_EXECUTION", "STALE_EXECUTION", "TARGET_NOT_FOUND", "ANALYSIS_TERMINAL", "ANALYSIS_CANCELLED")
                                        .contains(error.path("reasonCode").asText())) return null;
                            }
                            throw new RunPodAnalysisDeliveryException("runpod_cancel_http_" + status, status == 429 || status >= 500, null);
                        }
                        var json = contract.parse(response.getBody().readNBytes(RunPodContract.CONTROL_LIMIT + 1), "cancelResponse");
                        if (!requestId.toString().equals(json.path("requestId").asText())
                                || !executionId.toString().equals(json.path("executionId").asText())) {
                            throw new RunPodAnalysisDeliveryException("runpod_cancel_contract_invalid", false, null);
                        }
                        return null;
                    });
        } catch (RunPodAnalysisDeliveryException error) {
            throw error;
        } catch (RestClientException error) {
            throw new RunPodAnalysisDeliveryException("runpod_cancel_http_io_error", true, error);
        }
    }

    private record RunPodAnalysisCancelRequest(UUID executionId) {
    }
}
