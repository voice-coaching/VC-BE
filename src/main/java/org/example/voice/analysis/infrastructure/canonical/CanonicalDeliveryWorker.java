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
        var rows=jdbc.queryForList("SELECT handoff_sha256,state FROM analysis_canonical_handoffs WHERE handoff_id=?",entry.document.id());
        if(rows.isEmpty())return false;
        var row=rows.getFirst();
        if(!entry.document.digest().equals(row.get("handoff_sha256")))throw new RunPodContractException(409,"RESULT_EVENT_CONFLICT");
        if("COMMITTED".equals(row.get("state"))){entry.committed=true;entry.submitted=true;return true;}
        if(Set.of("REJECTED","REVOKED").contains(row.get("state"))){spool.reject(entry);return true;}
        return false;
    }
    private byte[] context(CanonicalDeliverySpool.Entry entry){
        var d=entry.document;var id=d.projection().identity();
        var rows=jdbc.queryForList("""
            SELECT j.request_bytes FROM analysis_results a
            JOIN analysis_canonical_executions e ON e.analysis_id=a.id AND e.execution_id::text=a.active_execution_id
                AND e.request_id::text=a.active_request_event_id
            JOIN analysis_canonical_journals j ON j.execution_id=e.execution_id
            JOIN voice_recordings r ON r.id=a.recording_id AND r.id=e.recording_id
            JOIN training_sessions s ON s.id=r.training_session_id AND s.content_id=e.content_id
            JOIN users u ON u.id=s.user_id JOIN practice_contents c ON c.id=s.content_id
            WHERE a.id=? AND e.execution_id=? AND e.request_id=? AND a.worker_instance_id=?
              AND e.recording_id=? AND e.content_id=? AND e.audio_sha256=r.audio_sha256
              AND e.request_payload_sha256=? AND j.event_id=? AND j.worker_instance_id=?
              AND j.worker_revision=? AND j.pipeline_revision=?
              AND e.result_schema_version=? AND a.expected_result_schema_version=e.result_schema_version
              AND a.status IN ('PENDING','PROCESSING') AND r.deleted_at IS NULL AND r.is_selected=TRUE
              AND s.status<>'CANCELED' AND u.status='ACTIVE' AND u.deleted_at IS NULL AND c.custom_deleted_at IS NULL
              AND (a.failure_code IS NULL OR a.failure_code NOT LIKE '%cancel%')
              AND e.deadline_at>=? AND a.execution_deadline_at>=? AND a.claim_expires_at>=?
            """,id.analysisId(),id.executionId(),id.requestId(),id.workerId().toString(),id.recordingId(),id.contentId(),
            d.metadata().path("requestSha256").asText(),id.eventId(),id.workerId(),d.projection().workerRevision(),d.projection().pipelineRevision(),
            RunPodContract.RESULT_V5,entry.receivedAt.atOffset(ZoneOffset.UTC),entry.receivedAt.atOffset(ZoneOffset.UTC),entry.receivedAt.atOffset(ZoneOffset.UTC));
        if(rows.size()!=1)throw new RunPodContractException(409,"EXECUTION_INACTIVE");
        return (byte[])rows.getFirst().get("request_bytes");
    }
    private void verify(){
        verificationTick=System.nanoTime();
        if(!spool.enabled() || !spool.operational())return;
        for(var e:pending()){
            if(e.verified || e.retryAt>System.currentTimeMillis())continue;
            long started=System.nanoTime();
            try{
                if(settled(e))return; // Recover a commit that completed immediately before restart.
                context(e); // Transport hashes were checked at receive/seal; no duplicate Python preflight.
                e.verified=true;e.retryAt=0;
                published.signal(e.document.projection().identity().analysisId());
                log("AVAILABLE",e,started);
            }catch(Exception error){failed(e,error,started);}return;
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
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("canonical_delivery_failure analysisId={} executionId={} type={} reason={}",
            e.document.projection().identity().analysisId(),e.document.projection().identity().executionId(),
            error.getClass().getSimpleName(),error instanceof RunPodContractException p?p.reason():
                error instanceof EvidenceFailure f?(f.retryable()?"EVIDENCE_UNAVAILABLE":"EVIDENCE_INVALID"):"DEPENDENCY_UNAVAILABLE");
        try{
            if((error instanceof EvidenceFailure f && !f.retryable()) || (error instanceof RunPodContractException p && (p.status()==409 || p.status()==422))){spool.reject(e);log("REJECTED",e,started);}
            else{e.attempts++;e.retryAt=System.currentTimeMillis()+Math.min(60000,1000L<<Math.min(e.attempts,6));log("RETRYING",e,started);}
        }catch(Exception ignored){log("SPOOL_UNAVAILABLE",e,started);}
    }
    private void log(String code,CanonicalDeliverySpool.Entry e,long started){org.slf4j.LoggerFactory.getLogger(getClass()).info("canonical_delivery analysisId={} executionId={} code={} attempts={} elapsedMs={}",e.document.projection().identity().analysisId(),e.document.projection().identity().executionId(),code,e.attempts,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));}
    @PreDestroy public void close(){verifyLane.shutdownNow();saveLane.shutdownNow();}
}
