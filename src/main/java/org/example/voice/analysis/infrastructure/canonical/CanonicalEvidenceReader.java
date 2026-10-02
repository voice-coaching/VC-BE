package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.http.apache5.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/** Private read-only client, not an S3Client bean: does not alter recording or TTS injection. */
@Component
public final class CanonicalEvidenceReader {
    private final CanonicalEvidenceSettings settings;
    private S3Client client;
    public CanonicalEvidenceReader(CanonicalEvidenceSettings settings){this.settings=settings;}
    private synchronized S3Client client() {
        if(!settings.configured())throw new EvidenceFailure(true);
        if(client==null) {
            try {
                client=S3Client.builder().region(Region.of(settings.region()))
                        .endpointOverride(URI.create(settings.endpoint()))
                        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(settings.keyId(),settings.applicationKey())))
                        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                        .httpClientBuilder(Apache5HttpClient.builder().connectionTimeout(Duration.ofSeconds(3))
                                .connectionAcquisitionTimeout(Duration.ofSeconds(3)).socketTimeout(Duration.ofSeconds(15))
                                .maxConnections(2).proxyConfiguration(ProxyConfiguration.builder()
                                        .useEnvironmentVariableValues(false).useSystemPropertyValues(false).build()))
                        .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(20))
                                .apiCallAttemptTimeout(Duration.ofSeconds(18)).retryPolicy(RetryPolicy.none()))
                        .build();
            } catch(Exception error){throw new EvidenceFailure(true);}
        }
        return client;
    }

    /** Readiness metadata only. Does not claim object readback or semantic verification. */
    public void assertPrivate() {
        try {
            var acl=client().getBucketAcl(b -> b.bucket(settings.bucket()));
            if(acl.owner()==null || acl.owner().id()==null || acl.owner().id().isBlank() || acl.grants().isEmpty())invalid();
            for(var grant:acl.grants()) {
                if(grant.grantee()==null || !"CanonicalUser".equals(grant.grantee().typeAsString())
                        || !acl.owner().id().equals(grant.grantee().id()) || !"FULL_CONTROL".equals(grant.permissionAsString())) invalid();
            }
        } catch(EvidenceFailure error){throw error;}
        catch(Exception error){throw new EvidenceFailure(true);}
    }

    public byte[] readVerified(long analysisId,UUID executionId,JsonNode artifact) {
        try {
            String version=artifact.path("versionId").asText();
            long length=artifact.path("byteSize").asLong(-1);
            String digest=artifact.path("sha256").asText();
            String key=settings.objectKey(analysisId,executionId,artifact.path("kind").asText(),digest);
            if(!key.equals(artifact.path("objectKey").asText()) || length<1 || length>16*1024*1024
                    || version.isEmpty() || version.length()>1024 || "null".equals(version)
                    || version.chars().anyMatch(c -> c<32 || c==127))invalid();
            assertPrivate();
            var request=GetObjectRequest.builder().bucket(settings.bucket()).key(key).versionId(version).build();
            try(var stream=client().getObject(request)) {
                try {
                    var metadata=stream.response();
                    if(!version.equals(metadata.versionId()) || metadata.contentLength()==null || metadata.contentLength()!=length
                            || !"application/json".equals(metadata.contentType()) || !"AES256".equals(metadata.serverSideEncryptionAsString())
                            || !digest.equals(metadata.metadata().get("sha256")))invalid();
                    long deadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();
                    var bytes=new java.io.ByteArrayOutputStream((int)length);
                    byte[] buffer=new byte[65536];
                    while(true) {
                        if(System.nanoTime()>=deadline)throw new EvidenceFailure(true);
                        int count=stream.read(buffer,0,(int)Math.min(buffer.length,length+1-bytes.size()));
                        if(count<0)break;
                        bytes.write(buffer,0,count);
                        if(bytes.size()>length)invalid();
                    }
                    byte[] raw=bytes.toByteArray();
                    if(raw.length!=length || !digest.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw))))invalid();
                    return raw;
                } catch(Exception error) {
                    // Do not drain a mismatched or oversized response while holding a connection.
                    stream.abort();
                    throw error;
                }
            }
        } catch(EvidenceFailure error){throw error;}
        catch(S3Exception error){throw new EvidenceFailure(error.statusCode()!=404);}
        catch(IllegalArgumentException error){throw new EvidenceFailure(false);}
        catch(Exception error){throw new EvidenceFailure(true);}
    }
    private static void invalid(){throw new EvidenceFailure(false);}
    @PreDestroy public synchronized void close(){if(client!=null){client.close();client=null;}}
}
