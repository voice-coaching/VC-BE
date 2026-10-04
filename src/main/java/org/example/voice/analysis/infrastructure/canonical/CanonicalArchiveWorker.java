package org.example.voice.analysis.infrastructure.canonical;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;

/** Archive outbox only. It cannot change user results or invoke inference/GPT. */
@Component
public final class CanonicalArchiveWorker {
    private final JdbcTemplate jdbc;private final CanonicalHandoffSettings settings;private final CanonicalArchiveWriter writer;
    private final TransactionTemplate tx;
    private final ScheduledExecutorService lane=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"canonical-archive");t.setDaemon(true);return t;});
    private final ExecutorService uploads=Executors.newFixedThreadPool(2,r->{var t=new Thread(r,"canonical-archive-artifact");t.setDaemon(true);return t;});
    private volatile long lastPoll;
    public CanonicalArchiveWorker(JdbcTemplate jdbc,CanonicalHandoffSettings settings,CanonicalArchiveWriter writer,PlatformTransactionManager manager){this.jdbc=jdbc;this.settings=settings;this.writer=writer;tx=new TransactionTemplate(manager);tx.setTimeout(5);}
    @PostConstruct public void start(){lane.scheduleWithFixedDelay(this::poll,1,2,TimeUnit.SECONDS);}
    public boolean operational(){return !lane.isShutdown() && lastPoll>0 && System.nanoTime()-lastPoll<TimeUnit.MINUTES.toNanos(6);}
    private void poll(){
        if(!settings.archiveEnabled())return;
        Map<String,Object> job=null;
        try {
            job=tx.execute(t->{
                var rows=jdbc.queryForList("""
                    SELECT j.*,h.analysis_id,h.execution_id FROM analysis_canonical_archive_jobs j
                    JOIN analysis_canonical_handoffs h USING(handoff_id)
                    WHERE j.state<>'ARCHIVED' AND j.next_attempt_at<=CURRENT_TIMESTAMP
                    AND (j.claim_until IS NULL OR j.claim_until<CURRENT_TIMESTAMP)
                    AND (j.state<>'RECONCILE_REQUIRED' OR EXISTS(SELECT 1 FROM analysis_canonical_archive_artifacts a WHERE a.handoff_id=j.handoff_id AND a.state NOT IN ('ARCHIVED','RECONCILE_REQUIRED')))
                    ORDER BY j.next_attempt_at LIMIT 1 FOR UPDATE OF j SKIP LOCKED
                    """);
                if(rows.isEmpty())return null;
                var row=rows.getFirst();var claim=UUID.randomUUID();row.put("claim_id",claim);
                // Crash after PUT intent is uncertain, never permission to PUT again.
                jdbc.update("UPDATE analysis_canonical_archive_artifacts SET state='RECONCILE_REQUIRED' WHERE handoff_id=? AND state='UPLOADING'",row.get("handoff_id"));
                jdbc.update("UPDATE analysis_canonical_archive_jobs SET state='UPLOADING',claim_id=?,claim_until=CURRENT_TIMESTAMP+INTERVAL '5 minutes',attempts=attempts+1 WHERE handoff_id=?",claim,row.get("handoff_id"));return row;
            });
            lastPoll=System.nanoTime();if(job==null)return;
            var current=job;
            var rows=jdbc.queryForList("""
                SELECT a.*,h.raw_bytes,h.sha256,h.byte_size FROM analysis_canonical_archive_artifacts a
                JOIN analysis_canonical_handoff_artifacts h USING(handoff_id,kind) WHERE a.handoff_id=? ORDER BY a.kind
                """,job.get("handoff_id"));
            var tasks=new ArrayList<Future<?>>();
            for(var item:rows)tasks.add(uploads.submit(()->archive(current,item)));
            for(var task:tasks)task.get();
            finish(job);
        } catch(Exception e){if(job!=null)try{finish(job);}catch(Exception ignored){/* Lease recovers after restart. */}}
    }
    private void archive(Map<String,Object> job,Map<String,Object> item){
        UUID handoff=(UUID)job.get("handoff_id");String kind=(String)item.get("kind"),state=(String)item.get("state");
        if(Set.of("ARCHIVED","RECONCILE_REQUIRED").contains(state))return;
        long started=System.nanoTime();
        String key=null,version=(String)item.get("version_id");boolean intent=false;
        try {
            String sha=(String)item.get("sha256");int size=((Number)item.get("byte_size")).intValue();byte[] raw=(byte[])item.get("raw_bytes");
            if(raw==null || raw.length!=size || !sha.equals(CanonicalCallbackDocument.sha256(raw)))throw new EvidenceFailure(false);
            key=writer.key(((Number)job.get("analysis_id")).longValue(),(UUID)job.get("execution_id"),kind,sha);
            if(version==null){
                writer.assertPrivate();
                int changed=jdbc.update("""
                    UPDATE analysis_canonical_archive_artifacts SET state='UPLOADING',definitive_rejection=FALSE,object_key=? WHERE handoff_id=? AND kind=?
                    AND state IN ('PENDING','RETRY_WAIT') AND EXISTS(SELECT 1 FROM analysis_canonical_archive_jobs WHERE handoff_id=? AND claim_id=? AND claim_until>CURRENT_TIMESTAMP)
                    """,key,handoff,kind,handoff,job.get("claim_id"));
                if(changed!=1)return;intent=true;
                version=writer.put(key,sha,raw);
                jdbc.update("UPDATE analysis_canonical_archive_artifacts SET version_id=?,state='VERIFYING' WHERE handoff_id=? AND kind=? AND version_id IS NULL AND state IN ('UPLOADING','RECONCILE_REQUIRED')",version,handoff,kind);
            }
            writer.verify(key,version,sha,size);
            jdbc.update("UPDATE analysis_canonical_archive_artifacts SET state='ARCHIVED' WHERE handoff_id=? AND kind=? AND state='VERIFYING' AND version_id=?",handoff,kind,version);
        }catch(Exception e){
            try{
                boolean rejected=e instanceof software.amazon.awssdk.services.s3.model.S3Exception remote
                    && Set.of(400,401,403,404,413,429).contains(remote.statusCode());
                if(intent && version==null && rejected)jdbc.update("UPDATE analysis_canonical_archive_artifacts SET state='RETRY_WAIT',definitive_rejection=TRUE WHERE handoff_id=? AND kind=? AND state='UPLOADING'",handoff,kind);
                else if(intent && version==null)jdbc.update("UPDATE analysis_canonical_archive_artifacts SET state='RECONCILE_REQUIRED' WHERE handoff_id=? AND kind=? AND state='UPLOADING'",handoff,kind);
                else if(!intent && version==null)jdbc.update("UPDATE analysis_canonical_archive_artifacts SET state='RETRY_WAIT' WHERE handoff_id=? AND kind=? AND state IN ('PENDING','RETRY_WAIT')",handoff,kind);
                // A known version remains VERIFYING and is read again, never re-uploaded.
            }catch(Exception ignored){}
        } finally {
            org.slf4j.LoggerFactory.getLogger(getClass()).info("canonical_archive_artifact analysisId={} executionId={} kind={} elapsedMs={}",job.get("analysis_id"),job.get("execution_id"),kind,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
        }
    }
    private void finish(Map<String,Object> job){
        var states=jdbc.queryForList("SELECT state FROM analysis_canonical_archive_artifacts WHERE handoff_id=?",String.class,job.get("handoff_id"));
        boolean archived=states.stream().allMatch("ARCHIVED"::equals);
        String state=archived?"ARCHIVED":states.contains("RECONCILE_REQUIRED")?"RECONCILE_REQUIRED":"RETRY_WAIT";
        long delay=Math.min(300,5L<<Math.min(6,((Number)job.get("attempts")).intValue()))+ThreadLocalRandom.current().nextLong(3);
        jdbc.update("""
            UPDATE analysis_canonical_archive_jobs SET state=?,claim_id=NULL,claim_until=NULL,
            next_attempt_at=CURRENT_TIMESTAMP+(?*INTERVAL '1 second'),archived_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,
            reason_code=? WHERE handoff_id=? AND claim_id=?
            """,state,delay,archived,archived?null:state,job.get("handoff_id"),job.get("claim_id"));
        org.slf4j.LoggerFactory.getLogger(getClass()).info("canonical_archive analysisId={} executionId={} state={}",job.get("analysis_id"),job.get("execution_id"),state);
    }
    /** Operator supplies an observed B2 version; exact-version readback proves it before checkpointing. */
    public void reconcile(UUID handoff,String kind,String version){
        var checkpoints=jdbc.queryForList("SELECT state,version_id FROM analysis_canonical_archive_artifacts WHERE handoff_id=? AND kind=?",handoff,kind);
        if(checkpoints.size()!=1)throw new org.example.voice.analysis.infrastructure.runpod.RunPodContractException(404,"TARGET_NOT_FOUND");
        var checkpoint=checkpoints.getFirst();
        if(version.equals(checkpoint.get("version_id")) && Set.of("VERIFYING","ARCHIVED").contains(checkpoint.get("state")))return;
        if(!"RECONCILE_REQUIRED".equals(checkpoint.get("state")) || checkpoint.get("version_id")!=null)throw new org.example.voice.analysis.infrastructure.runpod.RunPodContractException(409,"EVIDENCE_MANIFEST_CONFLICT");
        var row=jdbc.queryForMap("""
            SELECT h.analysis_id,h.execution_id,a.sha256,a.byte_size FROM analysis_canonical_handoffs h
            JOIN analysis_canonical_handoff_artifacts a USING(handoff_id) WHERE h.handoff_id=? AND a.kind=?
            """,handoff,kind);
        String key=writer.key(((Number)row.get("analysis_id")).longValue(),(UUID)row.get("execution_id"),kind,(String)row.get("sha256"));
        writer.verify(key,version,(String)row.get("sha256"),((Number)row.get("byte_size")).intValue());
        tx.executeWithoutResult(t->{
            jdbc.update("UPDATE analysis_canonical_archive_artifacts SET object_key=?,version_id=?,state='VERIFYING' WHERE handoff_id=? AND kind=? AND state='RECONCILE_REQUIRED' AND version_id IS NULL",key,version,handoff,kind);
            jdbc.update("UPDATE analysis_canonical_archive_jobs SET state='RETRY_WAIT',next_attempt_at=CURRENT_TIMESTAMP WHERE handoff_id=? AND state='RECONCILE_REQUIRED' AND claim_id IS NULL",handoff);
        });
    }
    @PreDestroy public void close(){lane.shutdownNow();uploads.shutdown();}
}
