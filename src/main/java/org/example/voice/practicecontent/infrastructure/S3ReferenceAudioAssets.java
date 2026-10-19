package org.example.voice.practicecontent.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.domain.port.ReferenceAudioAssets;
import org.example.voice.training.infrastructure.storage.ObjectStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "storage", name = "enabled", havingValue = "true")
public class S3ReferenceAudioAssets implements ReferenceAudioAssets {
    private final S3Presigner presigner;
    private final ObjectStorageProperties properties;

    @Override
    public Playback playback(String objectKey) {
        if (!isAllowedKey(objectKey)) {
            throw new IllegalStateException("REFERENCE_AUDIO_OBJECT_KEY_INVALID");
        }
        var request = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .responseContentType(contentType(objectKey))
                .build();
        var signed = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(request)
                .build());
        return new Playback(signed.url().toString(), signed.expiration().atOffset(ZoneOffset.UTC));
    }

    private boolean isAllowedKey(String objectKey) {
        return objectKey != null
                && objectKey.startsWith(properties.getReferenceAudiosPrefix())
                && !objectKey.contains("\\")
                && !objectKey.endsWith("/")
                && java.util.Arrays.stream(objectKey.split("/", -1))
                .noneMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals(".."));
    }

    private String contentType(String objectKey) {
        return objectKey.toLowerCase().endsWith(".m4a") ? "audio/mp4" : "audio/mpeg";
    }
}
