package org.example.voice.analysis.infrastructure.canonical;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.apache5.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;

/** Separate Backend writer credential. Never inherits recording credentials or the v4 reader key. */
@Component
public final class CanonicalArchiveWriter {
    private final CanonicalHandoffSettings settings;
    private S3Client client;
    public CanonicalArchiveWriter(CanonicalHandoffSettings settings){this.settings=settings;}
    private String value(String n){return settings.value("b2."+n);}
    public boolean configured(){
        String prefix=value("prefix");
        return value("region").matches("[a-z]{2}-[a-z]+-[0-9]{3}")
            && value("endpoint").equals("https://s3."+value("region")+".backblazeb2.com")
            && value("bucket").matches("[A-Za-z0-9][A-Za-z0-9-]{4,48}[A-Za-z0-9]")
            && prefix.length()<=800 && prefix.matches("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*/")
            && value("writer-key-id").matches("[A-Za-z0-9]+") && validApplicationKey(value("writer-application-key"))
            && "INDEFINITE".equals(settings.value("retention"));
    }
    /** Provider-issued opaque secret: preserve punctuation, reject unsafe env/control bytes. */
    private static boolean validApplicationKey(String key){
        return !key.isEmpty() && key.length()<=512 && key.chars().allMatch(c->c>32 && c<127);
    }
    private synchronized S3Client client(){
        if(!configured())throw new EvidenceFailure(true);
        if(client==null)client=S3Client.builder().region(Region.of(value("region"))).endpointOverride(URI.create(value("endpoint")))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(value("writer-key-id"),value("writer-application-key"))))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .httpClientBuilder(Apache5HttpClient.builder().maxConnections(2).connectionTimeout(Duration.ofSeconds(3))
                .connectionAcquisitionTimeout(Duration.ofSeconds(3)).socketTimeout(Duration.ofSeconds(15))
                .proxyConfiguration(ProxyConfiguration.builder().useEnvironmentVariableValues(false).useSystemPropertyValues(false).build()))
            .overrideConfiguration(c->c.apiCallTimeout(Duration.ofSeconds(20)).apiCallAttemptTimeout(Duration.ofSeconds(18)).retryPolicy(RetryPolicy.none())).build();
        return client;
    }
    public String key(long analysis,UUID execution,String kind,String sha){
        if(!configured() || analysis<1 || execution==null || !Set.of("CORE","BRIDGE_RESULT","BINDING","ASSOCIATION","SELECTION_PROJECTION","MEDIA_RECEIPT","VISUAL_EVIDENCE").contains(kind) || !sha.matches("[0-9a-f]{64}"))throw new EvidenceFailure(false);
        return value("prefix")+analysis+"/"+execution+"/"+kind+"/"+sha+".json";
    }
    public void assertPrivate(){
        var acl=client().getBucketAcl(b->b.bucket(value("bucket")));
        if(acl.owner()==null || acl.owner().id()==null || acl.grants().isEmpty())throw new EvidenceFailure(false);
        for(var grant:acl.grants())if(grant.grantee()==null || !"CanonicalUser".equals(grant.grantee().typeAsString())
            || !acl.owner().id().equals(grant.grantee().id()) || !"FULL_CONTROL".equals(grant.permissionAsString()))throw new EvidenceFailure(false);
    }
    /** One invocation is one PUT; caller must persist intent first and reconcile every uncertain outcome. */
    public String put(String key,String sha,byte[] raw){
        var response=client().putObject(PutObjectRequest.builder().bucket(value("bucket")).key(key).contentType("application/json")
            .serverSideEncryption(ServerSideEncryption.AES256).metadata(Map.of("sha256",sha)).build(),RequestBody.fromBytes(raw));
        String version=response.versionId();checkVersion(version);return version;
    }
    public void verify(String key,String version,String sha,int size){
        checkVersion(version);if(size<1 || size>16*1024*1024)throw new EvidenceFailure(false);
        assertPrivate();
        try(var stream=client().getObject(GetObjectRequest.builder().bucket(value("bucket")).key(key).versionId(version).build())){
            try {
                var meta=stream.response();
                if(!version.equals(meta.versionId()) || !Objects.equals(meta.contentLength(),(long)size)
                    || !"application/json".equals(meta.contentType()) || !"AES256".equals(meta.serverSideEncryptionAsString())
                    || !sha.equals(meta.metadata().get("sha256")))throw new EvidenceFailure(false);
                byte[] raw=stream.readNBytes(size+1);
                if(raw.length!=size || !sha.equals(CanonicalCallbackDocument.sha256(raw)))throw new EvidenceFailure(false);
            }catch(Exception e){stream.abort();throw e;}
        }catch(EvidenceFailure e){throw e;}catch(Exception e){throw new EvidenceFailure(true);}
    }
    private static void checkVersion(String v){if(v==null || v.isBlank() || v.length()>1024 || v.equals("null") || v.chars().anyMatch(c->c<32 || c==127))throw new EvidenceFailure(false);}
    @PreDestroy public synchronized void close(){if(client!=null)client.close();}
}
