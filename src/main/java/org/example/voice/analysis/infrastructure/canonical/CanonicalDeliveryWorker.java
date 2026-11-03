package org.example.voice.analysis.infrastructure.canonical;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** RunPod owns semantic preflight. AWS checks the current execution and persists asynchronously. */
@Component
public final class CanonicalDeliveryWorker {
    private final CanonicalDeliverySpool spool;
    private final CanonicalSemanticVerifier verifier;
    private final CanonicalHandoffStore database;
    private final JdbcTemplate jdbc;
    private final CanonicalPublishedResults published;
    private volatile long verificationTick,saveTick;
    private int saveCursor;
    public boolean operational(){return !spool.enabled() || (!verifyLane.isShutdown() && !saveLane.isShutdown() && System.nanoTime()-verificationTick<TimeUnit.SECONDS.toNanos(150) && System.nanoTime()-saveTick<TimeUnit.SECONDS.toNanos(150));}
    private final ScheduledExecutorService verifyLane=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"canonical-delivery-verify"));
    private final ScheduledExecutorService saveLane=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"canonical-delivery-save"));
    public CanonicalDeliveryWorker(CanonicalDeliverySpool spool,CanonicalSemanticVerifier verifier,CanonicalHandoffStore database,JdbcTemplate jdbc,CanonicalPublishedResults published){this.spool=spool;this.verifier=verifier;this.database=database;this.jdbc=jdbc;this.published=published;}
    @PostConstruct public void start(){verifyLane.scheduleWithFixedDelay(this::verify,1,1,TimeUnit.SECONDS);saveLane.scheduleWithFixedDelay(this::save,1,1,TimeUnit.SECONDS);}
    private List<CanonicalDeliverySpool.Entry> pending(){return spool.entries().stream().filter(e->e.receivedAt!=null && !e.rejected && !e.committed).sorted(Comparator.comparing(e->e.receivedAt)).toList();}
    private boolean settled(CanonicalDeliverySpool.Entry entry){
        var rows=jdbc.queryForList("SELECT state,handoff_sha256 FROM analysis_canonical_handoffs WHERE handoff_id=?",entry.document.id());
        if(rows.isEmpty()) return false;
        var current = rows.getFirst();
        if (!entry.document.digest().equals(current.get("handoff_sha256")))
            throw new RunPodContractException(409, "RESULT_EVENT_CONFLICT");
        if("COMMITTED".equals(current.get("state"))) { entry.committed=true;entry.submitted=true;return true; }
        if (Set.of("REJECTED", "REVOKED").contains(current.get("state"))) {
            entry.rejected=true;entry.verified=false;return true;
        }
        return false;
    }
    private byte[] context(CanonicalDeliverySpool.Entry entry){
        var rows=jdbc.queryForList("SELECT request_bytes FROM analysis_canonical_journals WHERE execution_id=?",
                entry.document.projection().identity().executionId());
        return rows.isEmpty() ? null : (byte[]) rows.getFirst().get("request_bytes");
    }
    private void verify(){
        verificationTick=System.nanoTime();
        for (var entry : pending()) {
            if (entry.verified) continue;
            entry.verified=true;
            published.signal(entry.document.projection().identity().analysisId());
        }
    }
    private void save(){
        saveTick=System.nanoTime();
        if(!spool.enabled() || !spool.operational())return;
        var entries = pending();
        // Rotate a bounded scan so waiting/failed early entries cannot starve later results.
        int size = entries.size();
        int start = size == 0 ? 0 : Math.floorMod(saveCursor, size);
        for (int checked = 0; checked < Math.min(size, 25); checked++) {
            int index = (start + checked) % size;
            var e = entries.get(index);
            saveCursor = (index + 1) % size;
            if(!e.verified || e.retryAt>System.currentTimeMillis())continue;
            long started=System.nanoTime();
            try{
                // Consult DB even after restart, before re-registering a completed handoff.
                if (settled(e)) continue;
                if (e.submitted) { e.retryAt=System.currentTimeMillis()+5000;continue; }
                var id=e.document.projection().identity();
                database.receiveDeferred(id.analysisId(),id.workerId(),spool.withOriginals(e));
                e.submitted=true;e.retryAt=0;log("DB_RECEIVED",e,started);
                return; // At most one new DB handoff per tick; pending entries do not consume it.
            }catch(Exception error){failed(e,error,started);}
        }
    }
    private void failed(CanonicalDeliverySpool.Entry e,Exception error,long started){
        e.attempts = Math.min(Integer.MAX_VALUE - 1, e.attempts) + 1;
        e.retryAt=System.currentTimeMillis()+TimeUnit.SECONDS.toMillis(Math.min(60L,1L << Math.min(e.attempts-1,6)));
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("canonical_delivery analysisId={} type={} sqlState={}",
                e.document.projection().identity().analysisId(),error.getClass().getSimpleName(),sqlState(error));
    }
    /** Classify persistence failures without logging SQL, connection strings, or result payloads. */
    static String sqlState(Throwable error) {
        for (int depth=0; error!=null && depth<12; depth++,error=error.getCause()) {
            if (error instanceof java.sql.SQLException sql && sql.getSQLState()!=null
                    && sql.getSQLState().matches("[A-Z0-9]{5}")) return sql.getSQLState();
        }
        return "UNKNOWN";
    }
    private void log(String code,CanonicalDeliverySpool.Entry e,long started){org.slf4j.LoggerFactory.getLogger(getClass()).info("canonical_delivery analysisId={} executionId={} code={} attempts={} elapsedMs={}",e.document.projection().identity().analysisId(),e.document.projection().identity().executionId(),code,e.attempts,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));}
    @PreDestroy public void close(){verifyLane.shutdownNow();saveLane.shutdownNow();}
}
