package org.example.voice.analysis.infrastructure.canonical;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** One bounded verification lane; does not occupy the legacy outbox/timeout scheduler. */
@Component
public final class CanonicalEvidenceWorker {
    private final CanonicalEvidenceSettings settings;
    private final CanonicalEvidenceJobs jobs;
    private final CanonicalArtifactLoader artifacts;
    private final CanonicalSemanticVerifier verifier;
    private final ScheduledExecutorService lane=Executors.newSingleThreadScheduledExecutor(r -> {
        var thread=new Thread(r,"canonical-evidence-verifier");thread.setDaemon(true);return thread;
    });
    private volatile boolean stopping;
    private volatile String lastReason="NOT_READY";
    public CanonicalEvidenceWorker(CanonicalEvidenceSettings settings,CanonicalEvidenceJobs jobs,
                                   CanonicalArtifactLoader artifacts,CanonicalSemanticVerifier verifier) {
        this.settings=settings;this.jobs=jobs;this.artifacts=artifacts;this.verifier=verifier;
    }
    @PostConstruct public void start(){lane.scheduleWithFixedDelay(this::poll,1,1,TimeUnit.SECONDS);}
    public String lastReason(){return lastReason;}
    private volatile long lastPoll;
    public boolean operational(){return !lane.isShutdown() && lastPoll!=0
        && System.nanoTime()-lastPoll<TimeUnit.SECONDS.toNanos(150);}
    private void poll() {
        if(stopping || !settings.verifierEnabled() || !settings.configured() || !settings.semanticConfigured()) {
            lastReason="NOT_READY";return;
        }
        CanonicalEvidenceJobs.Task task=null;
        try {
            task=jobs.claim();
            lastPoll=System.nanoTime();
            if(task==null)return;
            byte[] request=jobs.loadRequest(task);
            var claimed=task;
            var originals=artifacts.load(task.manifest(),task.analysisId(),task.executionId(),() -> fence(claimed));
            fence(task);
            verifier.verifyEvidence(request,task.manifest(),originals,
                    Duration.between(OffsetDateTime.now(ZoneOffset.UTC),task.deadline()));
            fence(task);
            jobs.finish(task,true,false);
            lastReason=null;
        } catch(EvidenceFailure error) {
            lastReason=error.getMessage();
            finishFailure(task,error.retryable());
        } catch(org.example.voice.analysis.infrastructure.runpod.RunPodContractException error) {
            boolean invalid=error.status()==400 || error.status()==422;
            lastReason=invalid?"EVIDENCE_INVALID":"DEPENDENCY_UNAVAILABLE";
            finishFailure(task,!invalid);
        } catch(Exception error) {
            // DB/lease/process errors contain private input in their messages; never log them.
            lastReason="DEPENDENCY_UNAVAILABLE";
            finishFailure(task,true);
        }
    }
    private void fence(CanonicalEvidenceJobs.Task task) {
        if(stopping || Thread.currentThread().isInterrupted() || !jobs.active(task))
            throw new EvidenceFailure(true);
    }
    private void finishFailure(CanonicalEvidenceJobs.Task task,boolean retryable) {
        if(task!=null)try{jobs.finish(task,false,retryable);}catch(Exception ignored){
            // Durable claim expires; a future owner re-reads the same exact versions, never generates AI output.
        }
    }
    @PreDestroy public void close(){stopping=true;lane.shutdownNow();}
}
