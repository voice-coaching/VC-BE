package org.example.voice.analysis.infrastructure.canonical;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Verifies retained callback bytes off the 10-second HTTP request path; never runs AI/GPT. */
@Component
public final class CanonicalCallbackVerifierWorker {
    private final CanonicalEvidenceSettings settings;
    private final CanonicalCallbackInbox inbox;
    private final CanonicalArtifactLoader artifacts;
    private final CanonicalSemanticVerifier verifier;
    private final RunPodContract contract;
    private final ScheduledExecutorService lane=Executors.newSingleThreadScheduledExecutor(r -> {
        var thread=new Thread(r,"canonical-callback-verifier");thread.setDaemon(true);return thread;
    });
    private volatile boolean stopping;
    private volatile String lastReason="NOT_READY";

    public CanonicalCallbackVerifierWorker(CanonicalEvidenceSettings settings,CanonicalCallbackInbox inbox,
                                           CanonicalArtifactLoader artifacts,CanonicalSemanticVerifier verifier,RunPodContract contract) {
        this.settings=settings;this.inbox=inbox;this.artifacts=artifacts;this.verifier=verifier;this.contract=contract;
    }
    @PostConstruct public void start(){lane.scheduleWithFixedDelay(this::poll,1,1,TimeUnit.SECONDS);}
    public String lastReason(){return lastReason;}
    private volatile long lastPoll;
    public boolean operational(){return !lane.isShutdown() && lastPoll!=0
        && System.nanoTime()-lastPoll<TimeUnit.SECONDS.toNanos(150);}
    private void poll() {
        if(stopping || !settings.callbackEnabled() || !settings.callbackVerifierEnabled()
                || !settings.semanticConfigured() || !settings.configured()) {lastReason="NOT_READY";return;}
        CanonicalCallbackInbox.Task task=null;
        try {
            task=inbox.claim();
            lastPoll=System.nanoTime();
            if(task==null)return;
            var doc=CanonicalCallbackDocument.parse(task.callback(),contract);
            var context=inbox.load(task,doc);
            var claimed=task;
            var originals=context.manifest()==null ? List.<byte[]>of()
                    : CanonicalTiming.measure(task.analysisId(),task.executionId(),"CALLBACK_READ",
                        () -> artifacts.loadForCallback(context.manifest(),claimed.analysisId(),claimed.executionId(),() -> fence(claimed)));
            fence(task);
            var budget=Duration.between(OffsetDateTime.now(ZoneOffset.UTC),task.deadline());
            CanonicalTiming.measure(task.analysisId(),task.executionId(),"CALLBACK_SEMANTIC", () -> {
                if(context.manifest()==null)verifier.verifyPrecore(context.request(),claimed.callback(),budget);
                else verifier.verifyCallback(context.request(),context.manifest(),originals,doc.retention().receiptId(),claimed.callback(),budget);
                return null;
            });
            fence(task);
            inbox.finish(task,true,false);
            lastReason=null;
        } catch(EvidenceFailure error) {
            lastReason=error.getMessage();finishFailure(task,error.retryable());
        } catch(RunPodContractException error) {
            boolean invalid=error.status()==400 || error.status()==422;
            lastReason=invalid?"EVIDENCE_INVALID":"DEPENDENCY_UNAVAILABLE";finishFailure(task,!invalid);
        } catch(Exception error) {
            lastReason="DEPENDENCY_UNAVAILABLE";finishFailure(task,true);
        }
    }
    private void fence(CanonicalCallbackInbox.Task task) {
        if(stopping || Thread.currentThread().isInterrupted())throw new EvidenceFailure(true);
        inbox.fence(task);
    }
    private void finishFailure(CanonicalCallbackInbox.Task task,boolean retryable) {
        if(task!=null)try{inbox.finish(task,false,retryable);}catch(Exception ignored){
            // Expiring durable claim allows the same bytes to be verified again; no new AI execution.
        }
    }
    @PreDestroy public void close(){stopping=true;lane.shutdownNow();}
}
