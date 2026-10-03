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
    public Claim acquire(String endpoint) {
        String key;
        try { key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(endpoint.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        jdbc.update("INSERT INTO analysis_dispatch_gates(endpoint_sha256) VALUES (?) ON CONFLICT DO NOTHING", key);
        var id = UUID.randomUUID();
        int updated = jdbc.update("""
            UPDATE analysis_dispatch_gates SET claim_id=?,claim_until=CURRENT_TIMESTAMP+INTERVAL '60 seconds'
            WHERE endpoint_sha256=? AND next_attempt_at<=CURRENT_TIMESTAMP
              AND (claim_until IS NULL OR claim_until<=CURRENT_TIMESTAMP)
            """, id, key);
        return updated == 1 ? new Claim(key, id) : null;
    }
    public void release(Claim claim, OffsetDateTime next) {
        jdbc.update("""
            UPDATE analysis_dispatch_gates SET claim_id=NULL,claim_until=NULL,next_attempt_at=?
            WHERE endpoint_sha256=? AND claim_id=?
            """, next, claim.endpoint(), claim.owner());
    }
}
