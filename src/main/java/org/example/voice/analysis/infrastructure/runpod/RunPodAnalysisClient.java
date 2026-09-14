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
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                        int status = response.getStatusCode().value();
                        boolean retryable = status == 429 || status >= 500;
                        throw new RunPodAnalysisDeliveryException("runpod_http_" + status, retryable, null);
                    })
                    .body(RunPodAnalysisJobAccepted.class);
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
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                        int status = response.getStatusCode().value();
                        boolean retryable = status == 429 || status >= 500;
                        throw new RunPodAnalysisDeliveryException("runpod_cancel_http_" + status, retryable, null);
                    })
                    .toBodilessEntity();
        } catch (RunPodAnalysisDeliveryException error) {
            throw error;
        } catch (RestClientException error) {
            throw new RunPodAnalysisDeliveryException("runpod_cancel_http_io_error", true, error);
        }
    }

    private record RunPodAnalysisCancelRequest(UUID executionId) {
    }
}
