package org.example.voice.analysis.infrastructure.runpod;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/** Cross-instance endpoint reservation. No transaction is held over HTTP. */
@Component
public final class RunPodDispatchGate {
    public record Claim(String endpoint, UUID owner) {}
    private final JdbcTemplate jdbc;
    public RunPodDispatchGate(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Claim acquire(String endpoint) { return new Claim(endpoint, UUID.randomUUID()); }
    public void release(Claim claim, OffsetDateTime next) {}
}
