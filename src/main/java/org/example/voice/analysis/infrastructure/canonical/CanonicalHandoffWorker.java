package org.example.voice.analysis.infrastructure.canonical;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.voice.analysis.application.CanonicalCallbackCommitter;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Durable verification lane; no B2 dependency and no HTTP transaction held during Python. */
@Component
public final class CanonicalHandoffWorker {
    private final JdbcTemplate jdbc; private final TransactionTemplate tx;
    private final CanonicalHandoffSettings settings; private final CanonicalHandoffStore store;
    private final CanonicalSemanticVerifier verifier; private final CanonicalCallbackCommitter committer;
    private final RunPodContract contract;
    private final ScheduledExecutorService lane=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"canonical-handoff");t.setDaemon(true);return t;});
    private volatile long lastPoll;
    private final CanonicalDeliverySpool delivery;
    public CanonicalHandoffWorker(JdbcTemplate jdbc,PlatformTransactionManager manager,CanonicalHandoffSettings settings,
        CanonicalHandoffStore store,CanonicalSemanticVerifier verifier,CanonicalCallbackCommitter committer,RunPodContract contract,CanonicalDeliverySpool delivery){
        this.delivery=delivery;
        this.jdbc=jdbc;this.settings=settings;this.store=store;this.verifier=verifier;this.committer=committer;this.contract=contract;
        tx=new TransactionTemplate(manager);tx.setTimeout(5);
    }
    public boolean operational(){return !lane.isShutdown() && lastPoll>0 && System.nanoTime()-lastPoll<TimeUnit.SECONDS.toNanos(150);}
    @PostConstruct public void start(){lane.scheduleWithFixedDelay(this::poll,1,1,TimeUnit.SECONDS);}
    private void poll(){
        if(!settings.workerEnabled())return;
        Map<String,Object> job=null;
        try {
            job=tx.execute(t->{
                var rows=jdbc.queryForList("""
                    SELECT * FROM analysis_canonical_handoffs WHERE
                    (state='RECEIVED' AND next_attempt_at<=CURRENT_TIMESTAMP) OR (state='VERIFYING' AND claim_until<CURRENT_TIMESTAMP)
                    ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED
                    """);
                if(rows.isEmpty())return null;
                var row=rows.getFirst();var owner=UUID.randomUUID();row.put("claim_id",owner);
                jdbc.update("UPDATE analysis_canonical_handoffs SET state='VERIFYING',reason_code=NULL,claim_id=?,claim_until=CURRENT_TIMESTAMP+INTERVAL '2 minutes',attempts=attempts+1 WHERE handoff_id=?",owner,row.get("handoff_id"));return row;
            });
            lastPoll=System.nanoTime();if(job==null)return;
            var current=job;
            tx.executeWithoutResult(t->store.active(current,true));
            var metadata=contract.parse((byte[])job.get("metadata_bytes"),"handoffMetadata");
            var raw=new ArrayList<byte[]>();
            for(var item:metadata.get("artifacts")) {
                byte[] bytes=jdbc.queryForObject("SELECT raw_bytes FROM analysis_canonical_handoff_artifacts WHERE handoff_id=? AND kind=?",byte[].class,job.get("handoff_id"),item.path("kind").asText());
                CanonicalHandoffDocument.checkBytes(item,bytes);raw.add(bytes);
            }
            long started=System.nanoTime();
            if(!delivery.enabled()){
                byte[] request=jdbc.queryForObject("SELECT request_bytes FROM analysis_canonical_journals WHERE execution_id=?",byte[].class,job.get("execution_id"));
                var deadline=jdbc.queryForObject("SELECT deadline_at FROM analysis_canonical_executions WHERE execution_id=?",java.time.OffsetDateTime.class,job.get("execution_id"));
                verifier.verifyHandoff(request,(byte[])job.get("metadata_bytes"),raw,(byte[])job.get("projection_bytes"),
                    Duration.between(java.time.Instant.now(),deadline.toInstant()));
            }
            org.slf4j.LoggerFactory.getLogger(getClass()).info("canonical_handoff_verify analysisId={} executionId={} elapsedMs={}",job.get("analysis_id"),job.get("execution_id"),TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
            var doc=CanonicalCallbackDocument.parse((byte[])job.get("projection_bytes"),contract);
            tx.executeWithoutResult(t->{
                store.active(current,true); // analysis lock before handoff lock
                var locked=jdbc.queryForMap("SELECT *,claim_until>CURRENT_TIMESTAMP AS claim_live FROM analysis_canonical_handoffs WHERE handoff_id=? FOR UPDATE",current.get("handoff_id"));
                if(!current.get("claim_id").equals(locked.get("claim_id")) || !"VERIFYING".equals(locked.get("state")) || !Boolean.TRUE.equals(locked.get("claim_live")))return;
                committer.commitHandoff(doc,(UUID)current.get("handoff_id"));
                jdbc.update("UPDATE analysis_canonical_handoffs SET state='COMMITTED',verified_at=CURRENT_TIMESTAMP,committed_at=CURRENT_TIMESTAMP,claim_id=NULL,claim_until=NULL WHERE handoff_id=?",current.get("handoff_id"));
            });
        } catch(Exception error) {
            if(job!=null)try {
                var failed=job;
                boolean revoked=error instanceof RunPodContractException e && e.status()==409;
                boolean invalid=(error instanceof EvidenceFailure e && !e.retryable())
                    || (error instanceof RunPodContractException contractError && contractError.status()==422);
                tx.executeWithoutResult(t->{
                    jdbc.queryForList("SELECT id FROM analysis_results WHERE id=? FOR UPDATE",failed.get("analysis_id"));
                    int changed=jdbc.update("""
                        UPDATE analysis_canonical_handoffs SET state=?,reason_code=?,claim_id=NULL,claim_until=NULL,
                        next_attempt_at=CURRENT_TIMESTAMP+INTERVAL '5 seconds' WHERE handoff_id=? AND claim_id=? AND state='VERIFYING'
                        """,revoked?"REVOKED":invalid?"REJECTED":"RECEIVED",revoked?"EXECUTION_INACTIVE":invalid?"EVIDENCE_INVALID":"VERIFIER_UNAVAILABLE",failed.get("handoff_id"),failed.get("claim_id"));
                    if(changed==1 && invalid)jdbc.update("""
                        UPDATE analysis_results SET status='FAILED',failure_code='canonical_handoff_invalid',failure_reason='Analysis evidence validation failed'
                        WHERE id=? AND active_execution_id=? AND status IN ('PENDING','PROCESSING')
                        """,failed.get("analysis_id"),failed.get("execution_id").toString());
                });
            } catch(Exception ignored){/* Expiring claim survives database outages. */}
        }
    }
    @PreDestroy public void close(){lane.shutdownNow();}
}
