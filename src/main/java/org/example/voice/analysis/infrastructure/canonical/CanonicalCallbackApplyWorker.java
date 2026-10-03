package org.example.voice.analysis.infrastructure.canonical;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.voice.analysis.application.CanonicalCallbackService;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.*;

/** Resume stored VERIFIED callback application, not AI/GPT, upload or lease renewal. */
@Component
public final class CanonicalCallbackApplyWorker {
    private final JdbcTemplate jdbc;
    private final CanonicalEvidenceSettings settings;
    private final CanonicalCallbackService callbacks;
    private final RunPodContract contract;
    private final ScheduledExecutorService lane=Executors.newSingleThreadScheduledExecutor(r->{
        var t=new Thread(r,"canonical-callback-apply");t.setDaemon(true);return t;
    });
    private volatile boolean stopping;
    private volatile String lastReason="NOT_READY";
    public CanonicalCallbackApplyWorker(JdbcTemplate jdbc,CanonicalEvidenceSettings settings,CanonicalCallbackService callbacks,RunPodContract contract) {
        this.jdbc=jdbc;this.settings=settings;this.callbacks=callbacks;this.contract=contract;
    }
    @PostConstruct public void start(){lane.scheduleWithFixedDelay(this::poll,1,1,TimeUnit.SECONDS);}
    public String lastReason(){return lastReason;}
    private volatile long lastPoll;
    public boolean operational(){return !lane.isShutdown() && lastPoll!=0
        && System.nanoTime()-lastPoll<TimeUnit.SECONDS.toNanos(150);}
    private void poll() {
        if(stopping || !settings.callbackApplyEnabled() || !settings.callbackEnabled()){lastReason="NOT_READY";return;}
        UUID event=null,claim=null;
        int attempt=0;
        try {
            claim=UUID.randomUUID();
            var rows=jdbc.queryForList("""
                    UPDATE analysis_canonical_callback_apply SET state='APPLYING',claim_id=?,
                        claim_until=CURRENT_TIMESTAMP+INTERVAL '30 seconds',attempts=attempts+1
                    WHERE event_id=(SELECT event_id FROM analysis_canonical_callback_apply
                        WHERE (state='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP)
                           OR (state='APPLYING' AND claim_until<=CURRENT_TIMESTAMP)
                        ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED)
                    RETURNING event_id,attempts
                    """,claim);
            lastPoll=System.nanoTime();
            if(rows.isEmpty())return;
            event=(UUID)rows.getFirst().get("event_id");attempt=((Number)rows.getFirst().get("attempts")).intValue();
            var raw=jdbc.queryForObject("SELECT callback_bytes FROM analysis_canonical_callback_inbox WHERE event_id=?",byte[].class,event);
            var doc=CanonicalCallbackDocument.parse(raw,contract);
            CanonicalTiming.measure(doc.identity().analysisId(),doc.identity().executionId(),"RESULT_COMMIT",
                    () -> callbacks.ingest(doc.identity().analysisId(),doc));
            finish(event,claim,"DONE",0);lastReason=null;
        } catch(RunPodContractException error) {
            lastReason="DEPENDENCY_UNAVAILABLE";
            boolean retry=error.status()==429 || error.status()==500 || error.status()==502 || error.status()==503 || error.status()==504;
            finishQuietly(event,claim,retry && attempt<16?"PENDING":"STOPPED",Math.min(30,1<<Math.min(attempt,4)));
        } catch(Exception error) {
            lastReason="DEPENDENCY_UNAVAILABLE";
            finishQuietly(event,claim,attempt<16?"PENDING":"STOPPED",30);
        }
    }
    private void finish(UUID event,UUID claim,String state,int delay) {
        jdbc.update("""
                UPDATE analysis_canonical_callback_apply SET state=?,claim_id=NULL,claim_until=NULL,
                    next_attempt_at=CURRENT_TIMESTAMP+(? * INTERVAL '1 second')
                WHERE event_id=? AND claim_id=? AND state='APPLYING'
                """,state,delay,event,claim);
    }
    private void finishQuietly(UUID event,UUID claim,String state,int delay) {
        if(event!=null)try{finish(event,claim,state,delay);}catch(Exception ignored){/* expiring claim, same immutable bytes */}
    }
    @PreDestroy public void close(){stopping=true;lane.shutdownNow();}
}
