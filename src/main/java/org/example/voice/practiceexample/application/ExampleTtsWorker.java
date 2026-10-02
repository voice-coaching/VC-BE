package org.example.voice.practiceexample.application;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice.practiceexample.domain.ExampleTtsFailure;
import org.example.voice.practiceexample.domain.port.ExampleTtsGenerator;
import org.example.voice.practiceexample.domain.port.ExampleTtsStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class ExampleTtsWorker {
    private final ExampleTtsProperties properties;
    private final ExampleTtsStore store;
    private final ExampleTtsGenerator generator;
    private final org.example.voice.practiceexample.domain.port.ExampleTtsAssets assets;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("example-tts").factory());
    private final AtomicBoolean busy=new AtomicBoolean();
    private volatile boolean halted;
    private long nextReconcile;
    @Scheduled(fixedDelayString="${example.tts.poll-ms:1000}")
    public void tick() {
        if(!properties.isWorkerEnabled() || !properties.configured() || halted || !busy.compareAndSet(false,true)) return;
        worker.submit(()->{
            try {
                if(System.nanoTime()>=nextReconcile) {
                    store.reconcile(properties.getRevision(),properties.getFingerprint());
                    nextReconcile=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                }
                store.claim(properties.getRevision()).ifPresent(job->{
                    try {
                        var audio=store.cached(job).orElseGet(()->generator.generate(job));
                        if(!store.stage(job,audio)) return;
                        assets.store(job,audio);
                        boolean accepted=store.complete(job,audio);
                        log.info("example_tts job={} result={}",job.id(),accepted ? "GENERATED":"STALE");
                    } catch(Exception e) {
                        boolean retry=e instanceof ExampleTtsFailure failure && failure.retryable();
                        String code=e instanceof ExampleTtsFailure ? e.getMessage():"TTS_INTERNAL_ERROR";
                        if(code.equals("TTS_AUTH") || code.equals("TTS_REVISION") || code.equals("TTS_STORAGE_AUTH")) halted=true;
                        long[] delays={10,30,120,600,600};
                        store.fail(job,code,retry,delays[Math.min(job.attempt()-1,4)]+ThreadLocalRandom.current().nextInt(5));
                        log.warn("example_tts job={} error={}",job.id(),code);
                    }
                });
            } catch(Exception e) { log.warn("example_tts scheduler failed type={}",e.getClass().getSimpleName()); }
            finally {busy.set(false);}
        });
    }
    @PreDestroy public void stop() {
        worker.shutdown();
        try { if(!worker.awaitTermination(35,TimeUnit.SECONDS)) worker.shutdownNow(); }
        catch(InterruptedException e){worker.shutdownNow();Thread.currentThread().interrupt();}
    }
}
