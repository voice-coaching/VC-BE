package org.example.voice.admin.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.model.AdminAudioData.*;
import org.example.voice.admin.domain.port.AdminAudioStorage;
import org.example.voice.common.exception.*;
import org.example.voice.practicecontent.domain.port.ReferenceAudioAssets;
import org.example.voice.training.infrastructure.storage.ObjectStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;
import java.util.stream.Collectors;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="storage",name="enabled",havingValue="true")
public class S3AdminAudioStorage implements AdminAudioStorage {
    private final S3Client s3;
    private final S3Presigner signer;
    private final ObjectStorageProperties properties;
    private final ReferenceAudioInspector inspector;
    private final ReferenceAudioAssets playback;

    @Override public void assertAvailable() { inspector.assertAvailable(); }

    @Override public SignedUpload sign(Upload upload) {
        try {
            var put=PutObjectRequest.builder().bucket(properties.getBucket()).key(stagingKey(upload))
                    .contentType(upload.contentType()).contentLength(upload.size()).ifNoneMatch("*")
                    .checksumSHA256(Base64.getEncoder().encodeToString(HexFormat.of().parseHex(upload.sha256()))).build();
            var signed=signer.presignPutObject(PutObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(10)).putObjectRequest(put).build());
            var headers=signed.signedHeaders().entrySet().stream()
                    .filter(h -> !h.getKey().equalsIgnoreCase("host") && !h.getKey().equalsIgnoreCase("content-length"))
                    .collect(Collectors.toMap(Map.Entry::getKey,h -> String.join(",",h.getValue())));
            return new SignedUpload(signed.url().toString(),headers,signed.expiration().atOffset(ZoneOffset.UTC));
        } catch(SdkException error) { throw unavailable(); }
    }
    @Override public VerifiedAudio verifyAndStore(Upload upload) {
        if(upload.size()<1 || upload.size()>org.example.voice.admin.domain.model.AdminAudioData.MAX_BYTES)throw invalid();
        try {
            var head=s3.headObject(HeadObjectRequest.builder().bucket(properties.getBucket()).key(stagingKey(upload))
                    .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(30)).apiCallAttemptTimeout(Duration.ofSeconds(10))).build());
            if(head.contentLength()==null || head.contentLength()!=upload.size() || !upload.contentType().equalsIgnoreCase(head.contentType()))throw invalid();
            byte[] bytes;
            try(var response=s3.getObject(GetObjectRequest.builder().bucket(properties.getBucket()).key(stagingKey(upload)).ifMatch(head.eTag())
                    .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(30)).apiCallAttemptTimeout(Duration.ofSeconds(10))).build())) {
                bytes=response.readNBytes((int)upload.size()+1);
            }
            if(bytes.length!=upload.size() || !upload.sha256().equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))throw invalid();
            int duration=inspector.validate(bytes,upload.extension());String key=finalKey(upload);
            try {
                s3.putObject(PutObjectRequest.builder().bucket(properties.getBucket()).key(key).contentType(upload.contentType())
                        .contentLength(upload.size()).ifNoneMatch("*").metadata(Map.of("sha256",upload.sha256()))
                        .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(30)).apiCallAttemptTimeout(Duration.ofSeconds(10))).build(),RequestBody.fromBytes(bytes));
            } catch(S3Exception error) {
                if(error.statusCode()!=412)throw error;
                // A retry may find its own immutable final object. Never overwrite a different one.
                var stored=s3.headObject(HeadObjectRequest.builder().bucket(properties.getBucket()).key(key)
                        .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(30)).apiCallAttemptTimeout(Duration.ofSeconds(10))).build());
                if(stored.contentLength()==null || stored.contentLength()!=upload.size() || !upload.contentType().equalsIgnoreCase(stored.contentType())
                        || !upload.sha256().equals(stored.metadata().get("sha256")))throw unavailable();
            }
            return new VerifiedAudio(key,duration);
        } catch(BaseException error) { throw error; }
        catch(S3Exception error) {
            if(error.statusCode()==404 || error.statusCode()==412)throw invalid();throw unavailable();
        } catch(Exception error) { throw unavailable(); }
    }
    @Override public Playback playback(Audio audio) {
        try { var link=playback.playback(audio.objectKey());return new Playback(audio.id(),link.url(),link.expiresAt()); }
        catch(RuntimeException error) { throw unavailable(); }
    }
    private String stagingKey(Upload u) { return prefix()+"uploads/"+u.id()+u.extension(); }
    private String finalKey(Upload u) { return prefix()+"announcer/"+u.contentId()+"/"+u.id()+u.extension(); }
    private String prefix() {
        String prefix=properties.getReferenceAudiosPrefix();
        if(prefix==null || !prefix.endsWith("/") || prefix.startsWith("/") || prefix.contains("\\")
                || Arrays.stream(prefix.split("/",-1)).limit(prefix.split("/",-1).length-1L).anyMatch(p -> p.isBlank() || p.equals(".") || p.equals("..")))throw unavailable();
        return prefix;
    }
    private static BaseException invalid() { return new BaseException(ErrorCode.ADMIN_AUDIO_INVALID); }
    private static BaseException unavailable() { return new BaseException(ErrorCode.ADMIN_AUDIO_UNAVAILABLE); }
}
