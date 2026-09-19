package org.example.voice.training.infrastructure.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@ConditionalOnProperty(prefix = "storage", name = "enabled", havingValue = "true")
public class S3ObjectStorageConfiguration {

    @Bean
    public S3Client recordingS3Client(ObjectStorageProperties properties) {
        validate(properties);
        var builder = S3Client.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.of(properties.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .build());
        if (hasText(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        return builder.build();
    }

    @Bean
    public S3Presigner recordingS3Presigner(ObjectStorageProperties properties) {
        validate(properties);
        var builder = S3Presigner.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.of(properties.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .build());
        if (hasText(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        return builder.build();
    }

    static void validate(ObjectStorageProperties properties) {
        String prefix = properties.getRecordingsPrefix();
        if (!hasText(properties.getBucket())
                || !hasText(properties.getRegion())
                || !hasText(prefix)
                || prefix.startsWith("/")
                || prefix.contains("\\")
                || prefix.contains("//")
                || !prefix.endsWith("/")
                || java.util.Arrays.stream(prefix.split("/"))
                        .anyMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals(".."))) {
            throw new IllegalStateException("object_storage_configuration_invalid");
        }
        if (hasText(properties.getEndpoint())) {
            URI endpoint;
            try {
                endpoint = URI.create(properties.getEndpoint());
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException("object_storage_configuration_invalid", error);
            }
            if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null) {
                throw new IllegalStateException("object_storage_configuration_invalid");
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
