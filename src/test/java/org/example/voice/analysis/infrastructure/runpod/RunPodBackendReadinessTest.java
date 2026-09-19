package org.example.voice.analysis.infrastructure.runpod;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunPodBackendReadinessTest {
    @Test
    void checksConfigurationAndDatabaseWithoutCallingWorker() {
        var jdbc = mock(JdbcTemplate.class);
        var properties = new RunPodAnalysisProperties();
        var readiness = new RunPodBackendReadiness(properties, jdbc, "runpod_http");
        assertThat(readiness.isReady()).isFalse();
        verifyNoInteractions(jdbc);
        properties.setEndpointUrl("https://worker.example");
        properties.setApiToken("api-token");
        properties.setCallbackToken("callback-token");
        properties.setCallbackBaseUrl("https://backend.example");
        assertThat(readiness.isReady()).isTrue();
        when(jdbc.queryForList(anyString())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
        assertThat(readiness.isReady()).isFalse();
    }
}
