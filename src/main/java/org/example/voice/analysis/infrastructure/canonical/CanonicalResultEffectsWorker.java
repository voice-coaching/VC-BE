package org.example.voice.analysis.infrastructure.canonical;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.voice.analysis.infrastructure.cache.AnalysisCacheKeys;
import org.example.voice.analysis.infrastructure.cache.AnalysisCacheNames;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Per-result cache effects only. No global clear, media/evidence deletion or wildcard keys. */
@Component
public final class CanonicalResultEffectsWorker {
    private record Claim(UUID event,UUID claim,long analysis,long user,long session) {}
    private final JdbcTemplate jdbc;
    private final CacheManager caches;
    private final CanonicalEvidenceSettings settings;
    private final TransactionTemplate transaction;
    private final ScheduledExecutorService lane=Executors.newSingleThreadScheduledExecutor(r -> {
        var thread=new Thread(r,"canonical-result-cache-effects");thread.setDaemon(true);return thread;
    });
    public CanonicalResultEffectsWorker(JdbcTemplate jdbc,CacheManager caches,CanonicalEvidenceSettings settings,
                                       PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.caches=caches;this.settings=settings;
        transaction=new TransactionTemplate(manager);transaction.setTimeout(5);
    }
    @PostConstruct public void start(){lane.scheduleWithFixedDelay(this::poll,1,1,TimeUnit.SECONDS);}
    private volatile long lastPoll;
    public boolean operational(){return !lane.isShutdown() && lastPoll!=0
        && System.nanoTime()-lastPoll<TimeUnit.SECONDS.toNanos(150);}
    private void poll() {
        if(!settings.callbackEnabled())return;
        Claim claim=null;
        try {
            claim=transaction.execute(tx -> {
                var rows=jdbc.queryForList("""
                        SELECT event_id,analysis_id,user_id,session_id FROM analysis_canonical_result_effects
                        WHERE completed_at IS NULL AND next_attempt_at<=CURRENT_TIMESTAMP
                            AND (claim_until IS NULL OR claim_until<=CURRENT_TIMESTAMP)
                        ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED
                        """);
                if(rows.isEmpty())return null;
                var row=rows.getFirst();
                var id=(UUID)row.get("event_id");var owner=UUID.randomUUID();
                jdbc.update("""
                        UPDATE analysis_canonical_result_effects SET claim_id=?,claim_until=CURRENT_TIMESTAMP+INTERVAL '1 minute',
                            attempts=attempts+1 WHERE event_id=?
                        """,owner,id);
                return new Claim(id,owner,((Number)row.get("analysis_id")).longValue(),
                        ((Number)row.get("user_id")).longValue(),((Number)row.get("session_id")).longValue());
            });
            lastPoll=System.nanoTime();
            if(claim==null)return;
            // Only these exact user's result/session keys. No row locks during Redis calls.
            evict(AnalysisCacheNames.DETAIL,AnalysisCacheKeys.owned(claim.user(),claim.analysis()));
            evict(AnalysisCacheNames.SESSION_RESULT,AnalysisCacheKeys.session(claim.user(),claim.session()));
            // Segment page keys are not enumerated: current-generation reads bypass that
            // cache for every analysis with canonical history; old entries expire by TTL.
            jdbc.update("""
                    UPDATE analysis_canonical_result_effects SET completed_at=CURRENT_TIMESTAMP,claim_id=NULL,claim_until=NULL
                    WHERE event_id=? AND claim_id=? AND completed_at IS NULL
                    """,claim.event(),claim.claim());
        } catch(Exception error) {
            if(claim!=null)try{
                jdbc.update("""
                        UPDATE analysis_canonical_result_effects SET claim_id=NULL,claim_until=NULL,
                            next_attempt_at=CURRENT_TIMESTAMP+INTERVAL '10 seconds'
                        WHERE event_id=? AND claim_id=? AND completed_at IS NULL
                        """,claim.event(),claim.claim());
            }catch(Exception ignored){/* Expiring claim allows idempotent recovery; no private diagnostics. */}
        }
    }
    private void evict(String name,String key) {
        var cache=caches.getCache(name);
        if(cache==null)throw new IllegalStateException("CACHE_UNAVAILABLE");
        cache.evict(key);
    }
    @PreDestroy public void close(){lane.shutdownNow();}
}
