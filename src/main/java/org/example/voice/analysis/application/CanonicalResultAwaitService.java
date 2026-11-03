package org.example.voice.analysis.application;

import jakarta.annotation.PreDestroy;
import org.example.voice.analysis.domain.model.CanonicalAnalysisView;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.infrastructure.canonical.CanonicalPublishedResults;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.springframework.stereotype.Service;
import java.util.concurrent.*;

/** A bounded event wait holds neither a servlet thread nor a database transaction. */
@Service
public final class CanonicalResultAwaitService {
    private final CanonicalAnalysisQueryService queries;
    private final CanonicalPublishedResults published;

    private final ExecutorService reads=Executors.newVirtualThreadPerTaskExecutor();
    public CanonicalResultAwaitService(CanonicalAnalysisQueryService queries,CanonicalPublishedResults published){this.queries=queries;this.published=published;}
    public CompletableFuture<CanonicalAnalysisView> get(long analysis,Long user,int waitSeconds){
        if(waitSeconds==0)return CompletableFuture.completedFuture(queries.get(analysis,user));
        var event=published.listen(analysis);
        try {
            var current=queries.get(analysis,user);
            if(current.jobStatus()!=AnalysisStatus.PENDING && current.jobStatus()!=AnalysisStatus.PROCESSING) {
                published.unlisten(analysis,event);return CompletableFuture.completedFuture(current);
            }
            return event.completeOnTimeout(null,waitSeconds,TimeUnit.SECONDS)
                .thenApplyAsync(ignored->queries.get(analysis,user),reads)
                .whenComplete((value,error)->published.unlisten(analysis,event));
        }catch(RuntimeException error){published.unlisten(analysis,event);throw error;}
    }
    @PreDestroy public void close(){reads.shutdownNow();}
}
