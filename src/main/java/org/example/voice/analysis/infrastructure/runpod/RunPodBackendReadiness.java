package org.example.voice.analysis.infrastructure.runpod;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RunPodBackendReadiness {
    private final RunPodAnalysisProperties properties;
    private final JdbcTemplate jdbc;
    private final String transport;

    public RunPodBackendReadiness(RunPodAnalysisProperties properties, JdbcTemplate jdbc,
                                 @Value("${analysis.transport:disabled}") String transport) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.transport = transport;
    }

    public boolean isReady() {
        if (!"runpod_http".equals(transport) || !properties.isConfigured()
                || properties.getClaimTtl().isNegative() || properties.getClaimTtl().isZero()) return false;
        try {
            // Probe the required persisted execution columns without creating a job or calling the worker.
            jdbc.queryForList("select active_execution_id, execution_deadline_at, worker_instance_id, claim_expires_at, last_result_payload_sha256 from analysis_results where 1=0");
            jdbc.queryForList("select payload, execution_id, transport from analysis_request_outbox where 1=0");
            return true;
        } catch (org.springframework.dao.DataAccessException error) { return false; }
    }
}
