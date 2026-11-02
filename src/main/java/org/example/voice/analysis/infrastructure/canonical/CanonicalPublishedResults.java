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

        var id=doc.identity();

        List<CompletableFuture<Void>> wake;
        synchronized(this){

            var old=entries.get(id.executionId());

            // Reject rather than evict an unrelated, still useful result. Full handoff remains available.
            int size=doc.bytes().length;

            entries.put(id.executionId(),new Entry(doc,Instant.now(),size));
            wake=List.copyOf(listeners.getOrDefault(analysis,Set.of()));
        }
        wake.forEach(f->f.complete(null));
    }

    public synchronized void match(CanonicalCallbackDocument doc){
        var entry=entries.get(doc.identity().executionId());

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

    /** One fresh authorization/current-attempt read. No result writes, JPA graph, or evidence verifier. */
    public CanonicalCallbackDocument current(long analysis,Long user){
        synchronized(this) {
            return entries.values().stream().filter(e -> e.document.identity().analysisId()==analysis)
                    .reduce((a,b)->b).map(Entry::document).orElse(null);
        }
    }
    @PreDestroy public synchronized void close(){listeners.values().forEach(s->s.forEach(f->f.cancel(false)));listeners.clear();entries.clear();}
}
