package org.example.voice.analysis.application;

import jakarta.annotation.PreDestroy;
import org.example.voice.analysis.domain.model.IndependentAnalysisAttempt;
import org.example.voice.analysis.domain.port.IndependentAnalysisClient;
import org.example.voice.analysis.domain.port.IndependentAudioSource;
import org.example.voice.analysis.infrastructure.independent.*;
import org.example.voice.analysis.infrastructure.cache.AnalysisCacheKeys;
import org.example.voice.analysis.infrastructure.cache.AnalysisCacheNames;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Separate bounded lanes: slow AI submission/collection/ACK never occupy the common scheduler. */
@Component
public final class IndependentAnalysisWorker {
    private final IndependentAnalysisProperties properties;
    private final IndependentAnalysisQueue queue;
    private final IndependentAnalysisClient client;
    private final IndependentAudioSource audio;
    private final CacheManager caches;
    private final Map<String,ExecutorService> lanes=new LinkedHashMap<>();
    private final Map<String,AtomicBoolean> running=new LinkedHashMap<>();
    public IndependentAnalysisWorker(IndependentAnalysisProperties properties,IndependentAnalysisQueue queue,
            IndependentAnalysisClient client,IndependentAudioSource audio,CacheManager caches) {
        this.properties=properties;this.queue=queue;this.client=client;this.audio=audio;this.caches=caches;
        for(String phase:List.of("SUBMIT","COLLECT","ACK")) {
            lanes.put(phase,Executors.newSingleThreadExecutor(r->{var thread=new Thread(r,"independent-analysis-"+phase.toLowerCase(Locale.ROOT));thread.setDaemon(true);return thread;}));
            running.put(phase,new AtomicBoolean());
        }
    }
    @Scheduled(fixedDelayString="${analysis.independent.poll-interval:PT1S}")
    public void poll() {
        if(!properties.isWorkersEnabled() || !properties.configured())return;
        lanes.forEach((phase,lane)->{
            var active=running.get(phase);if(!active.compareAndSet(false,true))return;
            try { lane.execute(()->{try { process(phase); } finally { active.set(false); }}); }
            catch(RejectedExecutionException shutdown) { active.set(false); }
        });
    }
    private void process(String phase) {
        IndependentAnalysisAttempt attempt=null;
        try {
            attempt=queue.claim(phase).orElse(null);if(attempt==null)return;
            switch(phase) {
                case "SUBMIT" -> submit(attempt);
                case "COLLECT" -> collect(attempt);
                case "ACK" -> acknowledge(attempt);
                default -> throw new IllegalArgumentException("Unknown independent phase");
            }
        } catch(Exception error) {
            var safe=error instanceof IndependentTransportException transport?transport:
                    new IndependentTransportException(0,"INDEPENDENT_LOCAL_UNAVAILABLE",0);
            if(attempt!=null) try { queue.retry(attempt,phase,safe); } catch(Exception unavailable) { /* Expiring DB claim resumes after outages. */ }
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("independent_analysis_deferred analysisId={} phase={} status={} code={}",
                    attempt==null?null:attempt.analysisId(),phase,safe.status(),safe.getMessage());
        }
    }
    private void submit(IndependentAnalysisAttempt attempt) {
        // Every uncertain POST is resolved by the original UUID before any same-bytes resubmission.
        var existing=client.find(attempt.clientRequestId());
        if(existing.isPresent()) { queue.accepted(attempt,existing.get());return; }
        if(!attempt.current()) { queue.canceledBeforeSubmit(attempt);return; }
        byte[] wav=audio.read(attempt);String script=queue.script(attempt);
        if(!queue.beforeSubmit(attempt))return;
        queue.accepted(attempt,client.submit(attempt,wav,script));
    }
    private void collect(IndependentAnalysisAttempt attempt) {
        var job=client.status(attempt.jobId());IndependentAnalysisQueue.correlate(attempt,job);
        if(!attempt.current() && !attempt.cancelRequested() && !job.terminal()) {
            job=client.cancel(attempt.jobId());IndependentAnalysisQueue.correlate(attempt,job);
            queue.cancelRequested(attempt);
        }
        if(!job.terminal()) { queue.waiting(attempt,job);return; }
        if(!job.resultAvailable()) {
            if("COMPLETED".equals(job.status()))throw new IndependentTransportException(0,"AI_RESULT_NOT_AVAILABLE",0);
            queue.terminalWithoutResult(attempt,job);return;
        }
        var raw=client.result(attempt.jobId());
        if(!Objects.equals(job.resultSha256(),raw.sha256()))throw new IndependentTransportException(0,"RESULT_DIGEST_MISMATCH",0);
        queue.remember(attempt,job,raw); // Original bytes survive a parser/public-adapter failure.
        var document=IndependentResultDocument.parse(raw.bytes(),attempt);
        if("COMPLETED".equals(job.status())) {
            var view=document.project();
            if(view.decision()==null || view.score()==null || view.score().validity()==null || view.score().reason()==null)
                throw new IndependentTransportException(0,"PUBLIC_RESULT_INCOMPLETE",0);
        }
        queue.complete(attempt,job,raw,document);
    }
    private void acknowledge(IndependentAnalysisAttempt attempt) {
        evict(AnalysisCacheNames.DETAIL,AnalysisCacheKeys.owned(attempt.ownerId(),attempt.analysisId()));
        evict(AnalysisCacheNames.SESSION_RESULT,AnalysisCacheKeys.session(attempt.ownerId(),attempt.sessionId()));
        if(attempt.resultSha256()==null) { queue.effectsWithoutResult(attempt);return; }
        queue.acknowledged(attempt,client.acknowledge(attempt.jobId(),attempt.resultSha256()));
    }
    private void evict(String name,String key) { var cache=caches.getCache(name);if(cache!=null)cache.evict(key); }
    @PreDestroy public void close() { lanes.values().forEach(ExecutorService::shutdownNow); }
}
