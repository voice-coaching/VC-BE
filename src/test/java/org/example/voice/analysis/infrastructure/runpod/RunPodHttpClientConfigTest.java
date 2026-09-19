package org.example.voice.analysis.infrastructure.runpod;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class RunPodHttpClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("analysis.transport=runpod_http")
            .withBean(RunPodAnalysisProperties.class, RunPodAnalysisProperties::new)
            .withUserConfiguration(RunPodHttpClientConfig.class, RunPodAnalysisClient.class);

    @Test
    void createsRunPodClientWhenHttpTransportIsEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RestClient.Builder.class);
            assertThat(context).hasSingleBean(RunPodAnalysisClient.class);
        });
    }
}
