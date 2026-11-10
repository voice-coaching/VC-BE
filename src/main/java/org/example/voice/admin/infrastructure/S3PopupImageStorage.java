package org.example.voice.admin.infrastructure;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.port.PopupImageStorage;
import org.example.voice.common.exception.*;
import org.example.voice.training.infrastructure.storage.ObjectStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component @RequiredArgsConstructor @ConditionalOnProperty(prefix="storage",name="enabled",havingValue="true")
public class S3PopupImageStorage implements PopupImageStorage {
    private final S3Client s3;
    private final S3Presigner signer;
    private final ObjectStorageProperties properties;
    private void validate(String key) {
        if(key==null||!key.matches("popup-images/[0-9a-f-]{36}\\.png"))throw new BaseException(ErrorCode.VALIDATION_ERROR);
    }
    public void upload(String key,byte[] png) {
        validate(key);
        try {
            s3.putObject(PutObjectRequest.builder().bucket(properties.getBucket()).key(key).contentType("image/png")
                    .cacheControl("private, no-store").ifNoneMatch("*")
                    .overrideConfiguration(c->c.apiCallTimeout(Duration.ofSeconds(30)).apiCallAttemptTimeout(Duration.ofSeconds(10))).build(),RequestBody.fromBytes(png));
        } catch(RuntimeException e){throw unavailable();}
    }
    public void delete(String key) {
        validate(key);
        try { s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.getBucket()).key(key)
                .overrideConfiguration(c->c.apiCallTimeout(Duration.ofSeconds(30)).apiCallAttemptTimeout(Duration.ofSeconds(10))).build()); }
        catch(RuntimeException e){throw unavailable();}
    }
    public String url(String key,Duration lifetime) {
        validate(key);
        try { return signer.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(lifetime)
                .getObjectRequest(GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build()).build()).url().toString(); }
        catch(RuntimeException e){throw unavailable();}
    }
    private BaseException unavailable(){return new BaseException(ErrorCode.ADMIN_STORE_UNAVAILABLE);}
}
