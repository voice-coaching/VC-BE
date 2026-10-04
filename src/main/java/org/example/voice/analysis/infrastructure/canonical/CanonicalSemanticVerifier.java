package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Pinned offline validator in an isolated child, no application credentials or network client. */
@Component
public final class CanonicalSemanticVerifier {
    private static final String PROTOCOL="voice-coaching.canonical-offline-verification.v1";
    private final CanonicalEvidenceSettings settings;
    private final ObjectMapper mapper=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public CanonicalSemanticVerifier(CanonicalEvidenceSettings settings){this.settings=settings;}

    /** Installation/pins only: no job, fixture, model inference or external network. */
    public void assertInstalled() {
        if(!settings.semanticConfigured())unavailable();
        Process child=null;
        try {
            var builder=new ProcessBuilder(settings.verifierPython(),"-I",settings.verifierScript(),
                    "--core-root",settings.coreRoot(),"--llm-root",settings.llmRoot(),
                    "--recordings-prefix",settings.recordingsPrefix(),"--check-installation");
            builder.environment().clear();
            builder.environment().put("PATH","/usr/bin:/bin");
            builder.environment().put("LANG","C.UTF-8");
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            child=builder.start();
            child.getOutputStream().close();
            if(!child.waitFor(15,TimeUnit.SECONDS))unavailable();
            byte[] raw=child.getInputStream().readNBytes(4097);
            if(raw.length>4096 || child.exitValue()!=0)unavailable();
            var response=mapper.readTree(raw);
            validateInstallation(response);
        } catch(InterruptedException error){Thread.currentThread().interrupt();unavailable();}
        catch(Exception error){unavailable();}
        finally {if(child!=null && child.isAlive())child.destroyForcibly();}
    }

    static void validateInstallation(JsonNode response) {
            if(response==null || !PROTOCOL.equals(response.path("protocol").asText())
                    || !"READY".equals(response.path("status").asText())
                    || !"95f2347ad53ab53030f68a65795cfc5a84ca1c8c41bd03119c9a9fb15d0367c2".equals(response.path("coreManifestSha256").asText())
                    || !"1413b196616c919aeaab243fdd57771e70221ffc53a37f9b018d1881499b8c73".equals(response.path("llmManifestSha256").asText())
                    || !"d8356763579dcfee347073a3f0ff72f4a0f67e33a29fa3b4c71012afcfd4ba01".equals(response.path("llmLockSha256").asText())
                    || !"9f10296b6944249ded9f5ce2ccfdfa7c53b6e4af670999fe68e9e477e97eaf45".equals(response.path("h5ManifestSha256").asText())
                    || !"93636f0c4befe7f1e34358e74b1077cc94cb2b82064781485b0183c53357d2f5".equals(response.path("h5LockSha256").asText())
                    || !"08fd8a19b6897d0c3890b0944986685830f24e6e676ea19422038b03a940ba80".equals(response.path("scoredCoreManifestSha256").asText())
                    || !"e3d6d6be9d896ede8d75d5f35f5daf4520a8d42f484e5f84a6fc32f8a05fb2e3".equals(response.path("scoredManifestSha256").asText())
                    || !"dc3db9cc03a3355ddeb5d5819cd6fd46726935c8ca9b830f513e37163c89f4d8".equals(response.path("scoredLockSha256").asText()))unavailable();
        // Older scored installations predate the optional resident package.
        if (response.has("residentCoreManifestSha256") || response.has("residentManifestSha256")
                || response.has("residentLockSha256")) {
            if (!"ce16f635da012237b5316b36161d5eeff9432279cd08dbbe7967f5ab7ace7f7e".equals(response.path("residentCoreManifestSha256").asText())
                    || !"885a2f0a67494dc3bebed28962222bfe95a0143292fe2de04194b84ea8beadc5".equals(response.path("residentManifestSha256").asText())
                    || !"1945dcf2fad7fc97621830abed34353c2d372d8224cc9ad11a9c422eabfdf9f4".equals(response.path("residentLockSha256").asText()))unavailable();
        }
    }

    public void verifyEvidence(byte[] request,byte[] manifest,List<byte[]> originals,Duration budget) {
        verify("VERIFY_EVIDENCE",request,manifest,originals,null,null,budget);
    }
    public void verifyCallback(byte[] request,byte[] manifest,List<byte[]> originals,UUID receipt,byte[] callback,Duration budget) {
        verify("VERIFY_CALLBACK",request,manifest,originals,receipt,callback,budget);
    }
    public void verifyPrecore(byte[] request,byte[] callback,Duration budget) {
        verify("VERIFY_PRECORE",request,null,List.of(),null,callback,budget);
    }

    private void verify(String mode,byte[] request,byte[] manifest,List<byte[]> originals,
                        UUID receipt,byte[] callback,Duration budget) {
        if(!settings.semanticConfigured() || budget.isNegative() || budget.isZero())unavailable();
        Process process=null;
        try {
            var builder=new ProcessBuilder(settings.verifierPython(),"-I",settings.verifierScript(),
                    "--core-root",settings.coreRoot(),"--llm-root",settings.llmRoot(),
                    "--recordings-prefix",settings.recordingsPrefix());
            builder.directory(Path.of(settings.verifierScript()).toAbsolutePath().getParent().toFile());
            builder.environment().clear();
            builder.environment().put("LANG","C.UTF-8");
            builder.environment().put("PATH","/usr/bin:/bin");
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            process=builder.start();
            var child=process;
            try(var tasks=Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory())) {
                var writer=tasks.submit(() -> {
                    try(var out=new DataOutputStream(child.getOutputStream())) {
                        var header=mapper.createObjectNode().put("protocol",PROTOCOL).put("operation",mode);
                        if(receipt==null)header.putNull("receiptId");else header.put("receiptId",receipt.toString());
                        frame(out,mapper.writeValueAsBytes(header),1024);
                        frame(out,request,65536);
                        if(manifest!=null) {
                            frame(out,manifest,65536);
                            if(originals.size()<4 || originals.size()>5)throw new IllegalArgumentException("ARTIFACT_COUNT");
                            for(byte[] raw:originals)frame(out,raw,16*1024*1024);
                        }
                        if(callback!=null)frame(out,callback,1024*1024);
                    }
                    return null;
                });
                var reader=tasks.submit(() -> child.getInputStream().readNBytes(4097));
                long timeout=Math.max(1,Math.min(100_000,budget.toMillis()));
                long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeout);
                try {
                    if(!child.waitFor(timeout,TimeUnit.MILLISECONDS))unavailable();
                    byte[] reply=reader.get(remaining(deadline),TimeUnit.NANOSECONDS);
                    if(reply.length>4096)unavailable();
                    JsonNode response=mapper.readTree(reply);
                    if(response==null || !PROTOCOL.equals(response.path("protocol").asText()))unavailable();
                    if(child.exitValue()==1 && "INVALID".equals(response.path("status").asText())
                            && "EVIDENCE_INVALID".equals(response.path("reasonCode").asText()))
                        throw new EvidenceFailure(false);
                    writer.get(remaining(deadline),TimeUnit.NANOSECONDS);
                    if(child.exitValue()!=0 || !"VALID".equals(response.path("status").asText())
                            || response.size()!=5 || !sha(request).equals(response.path("requestSha256").asText())
                            || !nullableSha(manifest,response.get("manifestSha256"))
                            || !nullableSha(callback,response.get("callbackSha256")))unavailable();
                } finally {
                    if(child.isAlive())child.destroyForcibly();
                    try{child.getOutputStream().close();}catch(Exception ignored){}
                    try{child.getInputStream().close();}catch(Exception ignored){}
                    writer.cancel(true);reader.cancel(true);
                }
            }
        } catch(EvidenceFailure error){throw error;}
        catch(InterruptedException error){Thread.currentThread().interrupt();unavailable();}
        catch(Exception error){unavailable();}
        finally {
            if(process!=null && process.isAlive())process.destroyForcibly();
        }
    }
    private static long remaining(long deadline){return Math.max(1,deadline-System.nanoTime());}
    private static void frame(DataOutputStream out,byte[] raw,int limit)throws java.io.IOException{
        if(raw==null || raw.length<1 || raw.length>limit)throw new IllegalArgumentException("FRAME_SIZE");
        out.writeInt(raw.length);out.write(raw);
    }
    private static boolean nullableSha(byte[] raw,JsonNode value){return value!=null && (raw==null?value.isNull():sha(raw).equals(value.asText()));}
    private static String sha(byte[] raw){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
        catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException("SHA256_UNAVAILABLE");}
    }
    private static void unavailable(){throw new EvidenceFailure(true);}
}
