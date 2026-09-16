package org.example.voice.profileimage.infrastructure;

import org.example.voice.profileimage.domain.ProfileImageException;
import org.example.voice.profileimage.domain.port.ProfileImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.net.URI;
import java.time.Duration;

@Configuration
public class ProfileImageStorageConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "profile-image", name = "enabled", havingValue = "false", matchIfMissing = true)
    ProfileImageStorage unavailableProfileImageStorage() {
        return new ProfileImageStorage() {
            public String imageUrl(String key) { throw ProfileImageException.unavailable(); }
            public void upload(String key, byte[] bytes) { throw ProfileImageException.unavailable(); }
            public void delete(String key) { throw ProfileImageException.unavailable(); }
        };
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "profile-image", name = "enabled", havingValue = "true")
    S3ProfileImageStorage s3ProfileImageStorage(@Value("${profile-image.bucket}") String bucket,
                                              @Value("${profile-image.region}") String region,
                                              @Value("${profile-image.cdn-base-url}") String cdn) {
        validateCdn(cdn);
        if (bucket.isBlank() || region.isBlank()) throw new IllegalArgumentException("profile_image_storage_configuration_invalid");
        S3Client client = S3Client.builder().region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(30)).apiCallAttemptTimeout(Duration.ofSeconds(10)))
                .build();
        return new S3ProfileImageStorage(client, bucket, cdn.replaceAll("/+$", ""));
    }

    static void validateCdn(String value) {
        URI uri;
        try { uri = URI.create(value); } catch (RuntimeException e) { throw new IllegalArgumentException("profile_image_cdn_invalid"); }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || uri.getPath().contains(".."))
            throw new IllegalArgumentException("profile_image_cdn_invalid");
    }

    static class S3ProfileImageStorage implements ProfileImageStorage, AutoCloseable {
        private final S3Client client;
        private final String bucket;
        private final String cdn;
        S3ProfileImageStorage(S3Client client, String bucket, String cdn) { this.client = client; this.bucket = bucket; this.cdn = cdn; }
        public String imageUrl(String key) { validateKey(key); return cdn + "/" + key; }
        public void upload(String key, byte[] bytes) {
            validateKey(key);
            try {
                client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("image/png")
                        .cacheControl("private, max-age=60").serverSideEncryption(ServerSideEncryption.AES256).build(), RequestBody.fromBytes(bytes));
            } catch (RuntimeException ignored) { throw ProfileImageException.unavailable(); }
        }
        public void delete(String key) {
            validateKey(key);
            try { client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()); }
            catch (RuntimeException ignored) { throw ProfileImageException.unavailable(); }
        }
        private void validateKey(String key) {
            if (key == null || !key.matches("profiles/[0-9a-f-]{36}\\.png")) throw new IllegalArgumentException("profile_image_key_invalid");
        }
        public void close() { client.close(); }
    }
}
