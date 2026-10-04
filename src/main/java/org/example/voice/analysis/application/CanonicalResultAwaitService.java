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
    private final Semaphore capacity=new Semaphore(128);
    private final ExecutorService reads=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),
        r->new Thread(r,"canonical-result-read"));
    public CanonicalResultAwaitService(CanonicalAnalysisQueryService queries,CanonicalPublishedResults published){this.queries=queries;this.published=published;}
    public CompletableFuture<CanonicalAnalysisView> get(long analysis,long user,int waitSeconds){
        if(waitSeconds<0 || waitSeconds>8)throw new RunPodContractException(422,"VALIDATION_FAILED");
        if(waitSeconds==0)return CompletableFuture.completedFuture(queries.get(analysis,user));
        if(!capacity.tryAcquire())throw new RunPodContractException(503,"RESULT_WAIT_CAPACITY");
        var event=published.listen(analysis); // Subscribe before the first read: no lost-publication race.
        try{
            var current=queries.get(analysis,user);
            if(current.jobStatus()!=AnalysisStatus.PENDING && current.jobStatus()!=AnalysisStatus.PROCESSING){
                published.unlisten(analysis,event);capacity.release();return CompletableFuture.completedFuture(current);
            }
            return event.completeOnTimeout(null,waitSeconds,TimeUnit.SECONDS)
                .thenApplyAsync(ignored->queries.get(analysis,user),reads)
                .whenComplete((value,error)->{published.unlisten(analysis,event);capacity.release();});
        }catch(RuntimeException error){published.unlisten(analysis,event);capacity.release();throw error;}
    }
    @PreDestroy public void close(){reads.shutdownNow();}
}
