package org.example.voice.analysis.infrastructure.canonical;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.voice.analysis.infrastructure.runpod.RunPodAnalysisProperties;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Additive, default-closed v6 readiness. No live inference or reference fabrication. */
@Component
public final class CanonicalAudiovisualReadiness {
    public static final String H5="d70372926d3406842b95c370b5ceb152480e7f0af83ba840028ec8d9ace2dc50";
    public static final List<String> SCHEMAS=List.of("runpod_analysis_request_v4.schema.json",
        "runpod_result_v6.schema.json","runpod_canonical_handoff_v2.schema.json",
        "runpod_media_preparation_v1.schema.json","runpod_visual_coaching_input_v1.schema.json");
    private final CanonicalDeliverySpool delivery;
    private final Environment environment;
    private final CanonicalHandoffReadiness handoff;
    private final CanonicalHandoffSettings settings;
    private final CanonicalSemanticVerifier verifier;
    private final RunPodContract contract;
    private final RunPodAnalysisProperties pod;
    private final JdbcTemplate jdbc;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ScheduledExecutorService lane=Executors.newSingleThreadScheduledExecutor(r->{
        var t=new Thread(r,"canonical-audiovisual-readiness");t.setDaemon(true);return t;});
    private volatile long installedUntil,podUntil;
    public CanonicalAudiovisualReadiness(Environment environment,CanonicalHandoffReadiness handoff,
            CanonicalHandoffSettings settings,CanonicalSemanticVerifier verifier,RunPodContract contract,
            RunPodAnalysisProperties pod,JdbcTemplate jdbc,CanonicalDeliverySpool delivery){
        this.delivery=delivery;this.environment=environment;this.handoff=handoff;this.settings=settings;this.verifier=verifier;
        this.contract=contract;this.pod=pod;this.jdbc=jdbc;
    }
    private boolean enabled(String key){return environment.getProperty("analysis.canonical.audiovisual."+key,Boolean.class,false);}
    public Map<String,String> digests(){var result=new LinkedHashMap<String,String>();for(var f:SCHEMAS)result.put(f,contract.schemaSha256(f));return result;}
    public boolean supported(){return enabled("worker-enabled") && handoff.supported() && System.nanoTime()<installedUntil;}
    public boolean admission(){try{return supported() && enabled("admission-enabled") && handoff.admission()
        && System.nanoTime()<podUntil && delivery.headroom(CanonicalHandoffSettings.AUDIOVISUAL_RESERVATION) && settings.used()+CanonicalHandoffSettings.AUDIOVISUAL_RESERVATION<=settings.budget();}
        catch(Exception ignored){return false;}}
    @PostConstruct public void start(){lane.scheduleWithFixedDelay(this::probe,2,15,TimeUnit.SECONDS);}
    private void probe(){
        if(!enabled("worker-enabled")){installedUntil=podUntil=0;return;}
        try{
            verifier.assertAudiovisualInstalled();
            if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM flyway_schema_history WHERE version='43' AND success=TRUE)",Boolean.class)))throw new IllegalStateException();
            digests();installedUntil=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
        }catch(Exception ignored){installedUntil=podUntil=0;return;}
        try{
            var uri=URI.create(pod.normalizedEndpointUrl()+"/health/audiovisual");
            if(!"https".equals(uri.getScheme()) || uri.getUserInfo()!=null)throw new IllegalStateException();
            var exchange=http.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                .header("Authorization","Bearer "+pod.getApiToken()).header("Accept-Encoding","identity").GET().build(), info -> new BoundedBody());
            try{
                var reply=exchange.get(8,TimeUnit.SECONDS);
                byte[] bytes=reply.body();
                if(reply.statusCode()!=200 || bytes.length>65536 || !reply.headers().firstValue("content-encoding").orElse("identity").equals("identity"))throw new IllegalStateException();
                var mapper=new ObjectMapper();var node=mapper.readTree(bytes);
                if(!"voice-coaching.canonical-handoff.v2".equals(node.path("contractVersion").asText())
                    || !node.path("executorConfigured").asBoolean() || !H5.equals(node.path("llmManifestSha256").asText())
                    || !digests().equals(mapper.convertValue(node.path("schemaDigests"),Map.class)))throw new IllegalStateException();
                podUntil=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);
            }finally{exchange.cancel(true);}
        }catch(Exception ignored){podUntil=0;}
    }
    /** Bound bytes and the complete response deadline, including a trickling body. */
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription value){subscription=value;value.request(1);}
        public void onNext(List<java.nio.ByteBuffer> chunks){
            for(var chunk:chunks){
                if(bytes.size()+chunk.remaining()>65536){subscription.cancel();result.completeExceptionally(new IllegalStateException());return;}
                byte[] value=new byte[chunk.remaining()];chunk.get(value);bytes.writeBytes(value);
            }
            subscription.request(1);
        }
        public void onError(Throwable error){result.completeExceptionally(error);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
    @PreDestroy public void close(){lane.shutdownNow();http.close();}
}
