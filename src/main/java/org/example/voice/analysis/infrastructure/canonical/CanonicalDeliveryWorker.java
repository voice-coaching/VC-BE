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
    public boolean operational(){return !spool.enabled() || (!verifyLane.isShutdown() && !saveLane.isShutdown() && System.nanoTime()-verificationTick<TimeUnit.SECONDS.toNanos(150) && System.nanoTime()-saveTick<TimeUnit.SECONDS.toNanos(150));}
    private final ScheduledExecutorService verifyLane=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"canonical-delivery-verify"));
    private final ScheduledExecutorService saveLane=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"canonical-delivery-save"));
    public CanonicalDeliveryWorker(CanonicalDeliverySpool spool,CanonicalSemanticVerifier verifier,CanonicalHandoffStore database,JdbcTemplate jdbc,CanonicalPublishedResults published){this.spool=spool;this.verifier=verifier;this.database=database;this.jdbc=jdbc;this.published=published;}
    @PostConstruct public void start(){verifyLane.scheduleWithFixedDelay(this::verify,1,1,TimeUnit.SECONDS);saveLane.scheduleWithFixedDelay(this::save,1,1,TimeUnit.SECONDS);}
    private List<CanonicalDeliverySpool.Entry> pending(){return spool.entries().stream().filter(e->e.receivedAt!=null && !e.rejected && !e.committed).sorted(Comparator.comparing(e->e.receivedAt)).toList();}
    private boolean settled(CanonicalDeliverySpool.Entry entry){
        var rows=jdbc.queryForList("SELECT state FROM analysis_canonical_handoffs WHERE handoff_id=?",entry.document.id());
        if(rows.isEmpty()) return false;
        if("COMMITTED".equals(rows.getFirst().get("state"))) { entry.committed=true;entry.submitted=true;return true; }
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
            entry.verified=true;
            entry.retryAt=0;
            published.signal(entry.document.projection().identity().analysisId());
        }
    }
    private void save(){
        saveTick=System.nanoTime();
        if(!spool.enabled() || !spool.operational())return;
        for(var e:pending()){
            if(!e.verified || e.retryAt>System.currentTimeMillis())continue;
            long started=System.nanoTime();
            try{
                if(e.submitted){if(!settled(e))e.retryAt=System.currentTimeMillis()+5000;return;}
                var id=e.document.projection().identity();
                database.receiveDeferred(id.analysisId(),id.workerId(),spool.withOriginals(e));
                e.submitted=true;e.retryAt=0;log("DB_RECEIVED",e,started);
            }catch(Exception error){failed(e,error,started);}return;
        }
    }
    private void failed(CanonicalDeliverySpool.Entry e,Exception error,long started){
        e.attempts++;
        e.retryAt=System.currentTimeMillis()+1000;
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("canonical_delivery analysisId={} type={}",
                e.document.projection().identity().analysisId(),error.getClass().getSimpleName());
    }
    private void log(String code,CanonicalDeliverySpool.Entry e,long started){org.slf4j.LoggerFactory.getLogger(getClass()).info("canonical_delivery analysisId={} executionId={} code={} attempts={} elapsedMs={}",e.document.projection().identity().analysisId(),e.document.projection().identity().executionId(),code,e.attempts,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));}
    @PreDestroy public void close(){verifyLane.shutdownNow();saveLane.shutdownNow();}
}
