package org.example.voice.analysis.infrastructure.canonical;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.example.voice.analysis.infrastructure.runpod.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.Duration;
import java.net.URI;
import java.net.http.*;

/** Independent v5 readiness; B2 availability does not gate result verification. */
@Component
public final class CanonicalHandoffReadiness {
    public static final List<String> SCHEMAS=List.of("runpod_http_control_v1.schema.json","runpod_http_control_v1_2.schema.json","runpod_canonical_parent.schema.json","runpod_canonical_input.schema.json","runpod_canonical_output.schema.json","runpod_canonical_journal_v1.schema.json","runpod_analysis_request_v3.schema.json","runpod_result_v5.schema.json","runpod_canonical_handoff_v1.schema.json");
    private final CanonicalHandoffSettings settings;private final CanonicalEvidenceSettings evidence;
    private final CanonicalSemanticVerifier verifier;private final CanonicalHandoffWorker worker;
    private final CanonicalArchiveWorker archive;private final CanonicalArchiveWriter writer;
    private final CanonicalResultEffectsWorker effects;private final RunPodContract contract;private final RunPodAnalysisProperties pod;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ScheduledExecutorService lane=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"canonical-handoff-readiness");t.setDaemon(true);return t;});
    private volatile long installedUntil,podUntil;
    private final CanonicalDeliverySpool delivery;
    private final CanonicalDeliveryWorker deliveryWorker;
    public CanonicalHandoffReadiness(CanonicalHandoffSettings settings,CanonicalEvidenceSettings evidence,CanonicalSemanticVerifier verifier,
        CanonicalHandoffWorker worker,CanonicalArchiveWorker archive,CanonicalArchiveWriter writer,CanonicalResultEffectsWorker effects,RunPodContract contract,RunPodAnalysisProperties pod,CanonicalDeliverySpool delivery,CanonicalDeliveryWorker deliveryWorker){
        this.deliveryWorker=deliveryWorker;
        this.delivery=delivery;
        this.settings=settings;this.evidence=evidence;this.verifier=verifier;this.worker=worker;this.archive=archive;this.writer=writer;this.effects=effects;this.contract=contract;this.pod=pod;
    }
    public Map<String,String> digests(){var result=new LinkedHashMap<String,String>();for(var file:SCHEMAS)result.put(file,contract.schemaSha256(file));return result;}
    public boolean supported(){return delivery.operational() && deliveryWorker.operational() && System.nanoTime()<installedUntil && settings.workerEnabled() && worker.operational() && effects.operational();}
    public boolean admission(){try{return supported() && delivery.headroom() && settings.admissionEnabled() && settings.archiveEnabled() && writer.configured() && archive.operational() && System.nanoTime()<podUntil && settings.used()+CanonicalHandoffSettings.RESERVATION<=settings.budget();}catch(Exception e){return false;}}
    @PostConstruct public void start(){lane.scheduleWithFixedDelay(this::probe,2,15,TimeUnit.SECONDS);}
    private void probe(){
        if(!settings.workerEnabled() || settings.budget()==0 || !evidence.journalEnabled() || !evidence.callbackEnabled())return;
        try{verifier.assertHandoffInstalled();settings.used();installedUntil=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);}catch(Exception e){installedUntil=podUntil=0;return;}
        try{
            var uri=URI.create(pod.normalizedEndpointUrl()+"/health/handoff");
            if(!"https".equals(uri.getScheme()) || uri.getUserInfo()!=null)throw new IllegalStateException();
            var reply=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+pod.getApiToken()).header("Accept-Encoding","identity").GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var stream=reply.body()){
                if(reply.statusCode()!=200 || !reply.headers().firstValue("content-encoding").orElse("identity").equals("identity"))throw new IllegalStateException();
                byte[] bytes=stream.readNBytes(65537);if(bytes.length>65536)throw new IllegalStateException();
                var node=new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes);
                if(!"voice-coaching.canonical-handoff.v1".equals(node.path("contractVersion").asText()) || !node.path("executorConfigured").asBoolean())throw new IllegalStateException();
                if(!digests().equals(new com.fasterxml.jackson.databind.ObjectMapper().convertValue(node.path("schemaDigests"),Map.class)))throw new IllegalStateException();
                podUntil=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);
            }
        }catch(Exception e){podUntil=0;}
    }
    @PreDestroy public void close(){lane.shutdownNow();http.close();}
}
