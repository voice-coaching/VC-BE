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
    public void assertPrivate() {}

    public byte[] readVerified(long analysisId,UUID executionId,JsonNode artifact) {
        try {
            String key=artifact.hasNonNull("objectKey") ? artifact.path("objectKey").asText()
                    : settings.objectKey(analysisId,executionId,artifact.path("kind").asText(),artifact.path("sha256").asText());
            var request=GetObjectRequest.builder().bucket(settings.bucket()).key(key).build();
            try(var stream=client().getObject(request)) { return stream.readAllBytes(); }
        } catch(Exception error) { throw new EvidenceFailure(true); }
    }

    @PreDestroy public synchronized void close(){if(client!=null){client.close();client=null;}}
}
