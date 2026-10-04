package org.example.voice.analysis.infrastructure.canonical;

import jakarta.annotation.PreDestroy;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Ephemeral delivery only. PUBLISHED never transfers durable execution responsibility. */
@Component
public final class CanonicalPublishedResults {
    private record Entry(CanonicalCallbackDocument document, Instant receivedAt, int size) {}
    private final Map<UUID,Entry> entries=new LinkedHashMap<>();
    private final Map<Long,Set<CompletableFuture<Void>>> listeners=new HashMap<>();
    private final JdbcTemplate jdbc;
    private final CanonicalDeliverySpool delivery;
    public CanonicalPublishedResults(JdbcTemplate jdbc,CanonicalDeliverySpool delivery){this.jdbc=jdbc;this.delivery=delivery;}

    public void publish(long analysis,UUID worker,CanonicalCallbackDocument doc){
        if(!delivery.enabled())throw new RunPodContractException(503,"RESULT_PUBLICATION_UNAVAILABLE");
        var id=doc.identity();
        if(analysis!=id.analysisId() || !worker.equals(id.workerId()) || !RunPodContract.RESULT_V5.equals(doc.schemaVersion())
            || !RunPodContract.HANDOFF_PROFILE.equals(doc.analysisProfile()))throw new RunPodContractException(409,"WORKER_CONFLICT");
        List<CompletableFuture<Void>> wake;
        synchronized(this){
            expire();
            var old=entries.get(id.executionId());
            if(old!=null && (!old.document.identity().equals(id) || !old.document.payloadSha256().equals(doc.payloadSha256())))
                throw new RunPodContractException(409,"RESULT_EVENT_CONFLICT");
            // Reject rather than evict an unrelated, still useful result. Full handoff remains available.
            int size=doc.bytes().length;
            if(old==null && (entries.size()>=128 || entries.values().stream().mapToLong(Entry::size).sum()+size>32*1024*1024))
                throw new RunPodContractException(503,"RESULT_PUBLICATION_UNAVAILABLE");
            if(old==null)entries.put(id.executionId(),new Entry(doc,Instant.now(),size));
            wake=List.copyOf(listeners.getOrDefault(analysis,Set.of()));
        }
        wake.forEach(f->f.complete(null));
    }

    public synchronized void match(CanonicalCallbackDocument doc){
        var entry=entries.get(doc.identity().executionId());
        if(entry!=null && (!entry.document.identity().equals(doc.identity()) || !entry.document.payloadSha256().equals(doc.payloadSha256())))
            throw new RunPodContractException(409,"RESULT_EVENT_CONFLICT");
    }

    public void signal(long analysis){
        List<CompletableFuture<Void>> wake;
        synchronized(this){wake=List.copyOf(listeners.getOrDefault(analysis,Set.of()));}
        wake.forEach(f->f.complete(null));
    }

    public synchronized CompletableFuture<Void> listen(long analysis){
        var future=new CompletableFuture<Void>();
        listeners.computeIfAbsent(analysis,k->new HashSet<>()).add(future);
        return future;
    }
    public synchronized void unlisten(long analysis,CompletableFuture<Void> future){
        var set=listeners.get(analysis);if(set!=null){set.remove(future);if(set.isEmpty())listeners.remove(analysis);}
    }
    private void expire(){entries.values().removeIf(e->e.receivedAt.isBefore(Instant.now().minusSeconds(3600)));}

    /** One fresh authorization/current-attempt read. No result writes, JPA graph, or evidence verifier. */
    public CanonicalCallbackDocument current(long analysis,long user){
        synchronized(this){expire();if(entries.values().stream().noneMatch(e->e.document.identity().analysisId()==analysis))return null;}
        var rows=jdbc.queryForList("""
            SELECT e.execution_id,e.request_id,e.recording_id,e.content_id,e.audio_sha256,e.script_sha256,j.event_id,
                j.worker_instance_id,j.worker_revision,j.pipeline_revision,e.deadline_at,a.claim_expires_at,
                a.execution_deadline_at
            FROM analysis_results a
            JOIN analysis_canonical_executions e ON e.analysis_id=a.id AND e.execution_id::text=a.active_execution_id
                AND e.request_id::text=a.active_request_event_id
            JOIN analysis_canonical_journals j ON j.execution_id=e.execution_id AND j.worker_instance_id::text=a.worker_instance_id
            JOIN voice_recordings r ON r.id=a.recording_id AND r.id=e.recording_id AND r.audio_sha256=e.audio_sha256
            JOIN training_sessions s ON s.id=r.training_session_id AND s.content_id=e.content_id
            JOIN users u ON u.id=s.user_id JOIN practice_contents c ON c.id=s.content_id
            WHERE a.id=? AND u.id=? AND a.status IN ('PENDING','PROCESSING')
                AND r.deleted_at IS NULL AND r.is_selected=TRUE AND s.status<>'CANCELED'
                AND u.status='ACTIVE' AND u.deleted_at IS NULL AND c.custom_deleted_at IS NULL
                AND (a.failure_code IS NULL OR a.failure_code NOT LIKE '%cancel%')
                AND a.analysis_profile=? AND e.request_schema_version=? AND e.result_schema_version=?
                AND a.expected_result_schema_version=e.result_schema_version
            """,analysis,user,RunPodContract.HANDOFF_PROFILE,RunPodContract.REQUEST_V3,RunPodContract.RESULT_V5);
        if(rows.size()!=1)return null;
        var r=rows.getFirst();Entry entry;
        synchronized(this){entry=entries.get((UUID)r.get("execution_id"));}
        if(entry==null)return null;
        var d=entry.document;var id=d.identity();
        if(id.analysisId()!=analysis || !id.requestId().equals(r.get("request_id")) || !id.eventId().equals(r.get("event_id"))
            || !id.workerId().equals(r.get("worker_instance_id")) || id.recordingId()!=((Number)r.get("recording_id")).longValue()
            || id.contentId()!=((Number)r.get("content_id")).longValue() || !d.source().audioSha256().equals(r.get("audio_sha256"))
            || !d.source().scriptSha256().equals(r.get("script_sha256"))
            || !d.workerRevision().equals(r.get("worker_revision")) || !d.pipelineRevision().equals(r.get("pipeline_revision")))return null;
        for(String key:List.of("deadline_at","execution_deadline_at","claim_expires_at")){
            Object value=r.get(key);
            Instant until=value instanceof java.sql.Timestamp t?t.toInstant():value instanceof OffsetDateTime t?t.toInstant():null;
            if(until==null || until.isBefore(entry.receivedAt))return null;
        }
        return d;
    }
    @PreDestroy public synchronized void close(){listeners.values().forEach(s->s.forEach(f->f.cancel(false)));listeners.clear();entries.clear();}
}
