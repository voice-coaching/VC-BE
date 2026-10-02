package org.example.voice.analysis.infrastructure.canonical;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.voice.analysis.infrastructure.runpod.RunPodAnalysisProperties;
import org.example.voice.analysis.infrastructure.runpod.RunPodBackendReadiness;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/** Server readiness, independent of FE tests and of user-provided request headers. */
@Component
public final class CanonicalBackendReadiness {
    public static final List<String> SCHEMAS=List.of(
        "runpod_http_control_v1.schema.json","runpod_result_v1.schema.json",
        "runpod_analysis_request_v2.schema.json","runpod_http_control_v1_2.schema.json",
        "runpod_result_v4.schema.json","runpod_canonical_parent.schema.json",
        "runpod_canonical_input.schema.json","runpod_canonical_output.schema.json",
        "runpod_canonical_journal_v1.schema.json");
    private final CanonicalEvidenceSettings settings;
    private final CanonicalEvidenceReader reader;
    private final CanonicalSemanticVerifier verifier;
    private final CanonicalEvidenceWorker evidenceWorker;
    private final CanonicalCallbackVerifierWorker callbackWorker;
    private final CanonicalCallbackApplyWorker applyWorker;
    private final CanonicalResultEffectsWorker effectsWorker;
    private final JdbcTemplate jdbc;
    private final RunPodBackendReadiness legacy;
    private final RunPodAnalysisProperties pod;
    private final RunPodContract contract;
    private final Environment environment;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ScheduledExecutorService lane=Executors.newSingleThreadScheduledExecutor(r -> {
        var t=new Thread(r,"canonical-readiness");t.setDaemon(true);return t;
    });
    private volatile long infrastructureUntil,workerUntil;
    private volatile String reason="NOT_READY";
    public CanonicalBackendReadiness(CanonicalEvidenceSettings settings,CanonicalEvidenceReader reader,
        CanonicalSemanticVerifier verifier,CanonicalEvidenceWorker evidenceWorker,
        CanonicalCallbackVerifierWorker callbackWorker,CanonicalCallbackApplyWorker applyWorker,
        CanonicalResultEffectsWorker effectsWorker,JdbcTemplate jdbc,RunPodBackendReadiness legacy,
        RunPodAnalysisProperties pod,RunPodContract contract,Environment environment) {
        this.settings=settings;this.reader=reader;this.verifier=verifier;this.evidenceWorker=evidenceWorker;
        this.callbackWorker=callbackWorker;this.applyWorker=applyWorker;this.effectsWorker=effectsWorker;
        this.jdbc=jdbc;this.legacy=legacy;this.pod=pod;this.contract=contract;this.environment=environment;
    }
    @PostConstruct public void start(){lane.scheduleWithFixedDelay(this::probe,1,15,TimeUnit.SECONDS);}
    private boolean configured() {
        return settings.configured() && settings.semanticConfigured() && settings.registrationEnabled()
            && settings.verifierEnabled() && settings.callbackEnabled() && settings.callbackVerifierEnabled()
            && settings.journalEnabled() && settings.callbackApplyEnabled()
            && settings.journalStagingBudgetBytes()>0;
    }
    public boolean supported() {
        return configured() && System.nanoTime()<infrastructureUntil
            && evidenceWorker.operational() && callbackWorker.operational()
            && applyWorker.operational() && effectsWorker.operational();
    }
    public boolean admissionEnabled() {
        return supported() && System.nanoTime()<workerUntil
            && "true".equals(environment.getProperty("analysis.canonical.admission-enabled"));
    }
    public String reason(){return admissionEnabled()?null:reason;}
    private void probe() {
        if(!configured()){infrastructureUntil=workerUntil=0;reason="CONFIGURATION_NOT_READY";return;}
        try {
            if(!legacy.isReady())throw new IllegalStateException();
            for(String table:List.of("analysis_canonical_executions","analysis_evidence_receipts",
                    "analysis_canonical_callback_inbox","analysis_canonical_results",
                    "analysis_canonical_journals",
                    "analysis_canonical_upload_journal","analysis_canonical_ack_journal",
                    "analysis_canonical_callback_apply","analysis_canonical_result_effects")) {
                jdbc.queryForList("SELECT * FROM "+table+" WHERE false");
            }
            Long bytes=jdbc.queryForObject("SELECT COALESCE(SUM(byte_size),0) FROM analysis_canonical_upload_journal",Long.class);
            // Reserve headroom for a complete five-artifact set, not a partial PUT.
            if(bytes==null || bytes+5L*16*1024*1024>settings.journalStagingBudgetBytes())throw new IllegalStateException();
            reader.assertPrivate();
            verifier.assertInstalled();
            infrastructureUntil=System.nanoTime()+Duration.ofSeconds(90).toNanos();
        } catch(Exception error){infrastructureUntil=workerUntil=0;reason="INFRASTRUCTURE_NOT_READY";return;}
        try {
            URI uri=URI.create(pod.normalizedEndpointUrl()+"/health/canonical");
            if(!"https".equals(uri.getScheme()) || uri.getUserInfo()!=null)throw new IllegalStateException();
            var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                .header("Authorization","Bearer "+pod.getApiToken()).header("Accept-Encoding","identity").GET().build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(var body=response.body()) {
                if(response.statusCode()!=200 || !"identity".equals(response.headers().firstValue("content-encoding").orElse("identity")))
                    throw new IllegalStateException();
                var doc=contract.parse(body.readNBytes(65537),"canonicalExecutorReadiness");
                if(!doc.path("executorConfigured").asBoolean())throw new IllegalStateException();
                var digests=doc.path("schemaDigests");
                if(digests.size()!=SCHEMAS.size())throw new IllegalStateException();
                for(String file:SCHEMAS) {
                    int matches=0;
                    for(var row:digests)if(file.equals(row.path("file").asText())
                        && contract.schemaSha256(file).equals(row.path("sha256").asText()))matches++;
                    if(matches!=1)throw new IllegalStateException();
                }
            }
            workerUntil=System.nanoTime()+Duration.ofSeconds(45).toNanos();
            reason="true".equals(environment.getProperty("analysis.canonical.admission-enabled"))?null:"ADMISSION_DISABLED";
        } catch(InterruptedException error){Thread.currentThread().interrupt();workerUntil=0;reason="WORKER_NOT_READY";}
        catch(Exception error){workerUntil=0;reason="WORKER_NOT_READY";}
    }
    @PreDestroy public void close(){infrastructureUntil=workerUntil=0;lane.shutdownNow();http.close();}
}
