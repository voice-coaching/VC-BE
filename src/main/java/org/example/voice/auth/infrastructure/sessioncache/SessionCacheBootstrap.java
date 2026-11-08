package org.example.voice.auth.infrastructure.sessioncache;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.OffsetDateTime;

/** The Redis bootstrap gate is installed before this DB snapshot is captured. */
@Component
public class SessionCacheBootstrap {
    private final JdbcTemplate jdbc;
    public SessionCacheBootstrap(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean awaitOlderTransactions(Duration timeout) {
        OffsetDateTime cutoff = jdbc.queryForObject(
                "SELECT CASE WHEN current_setting('track_activities')='on' THEN clock_timestamp() ELSE NULL END", OffsetDateTime.class);
        if (cutoff == null) return false;
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            // Exclude this connection: a first login may initialize the cache inside its own transaction.
            // Clear stats snapshots so a surrounding transaction does not retain an old pg_stat_activity view.
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            Boolean pending = jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                    WHERE datname=current_database() AND usename=current_user
                      AND pid<>pg_backend_pid() AND xact_start<=?)
                    """, Boolean.class, cutoff);
            if (Boolean.FALSE.equals(pending)) return true;
            try { Thread.sleep(50); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
        } while (System.nanoTime() < deadline);
        return false;
    }
}
