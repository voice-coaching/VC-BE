package org.example.voice.practiceexample.infrastructure;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.example.voice.practiceexample.domain.port.ExampleTtsAssets;
import org.example.voice.practiceexample.domain.model.ExampleTtsData.*;
import org.example.voice.practiceexample.domain.ExampleTtsFailure;
import org.example.voice.training.infrastructure.storage.ObjectStorageProperties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import java.time.*;
import java.util.Map;
@Component @RequiredArgsConstructor
public class S3ExampleTtsAssets implements ExampleTtsAssets {
    private final ObjectProvider<S3Client> clients;
    private final ObjectProvider<S3Presigner> signers;
    private final ObjectStorageProperties properties;
    public void store(Job job,Generated audio) {
        if(!properties.isEnabled() || clients.getIfAvailable()==null) throw new ExampleTtsFailure("TTS_STORAGE_CONFIGURATION",false);
        try {
            clients.getObject().putObject(PutObjectRequest.builder().bucket(properties.getBucket()).key(ExampleTtsAssets.key(job,audio))
                .contentType("audio/mpeg").metadata(Map.of("sha256",audio.sha256()))
                .overrideConfiguration(AwsRequestOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(15)).build()).build(),RequestBody.fromBytes(audio.bytes()));
        } catch(S3Exception e){throw new ExampleTtsFailure(e.statusCode()==403 ? "TTS_STORAGE_AUTH":"TTS_STORAGE_WRITE",e.statusCode()!=403);}
        catch(Exception e){throw new ExampleTtsFailure("TTS_STORAGE_WRITE",true);}
    }
    public Playback playback(String key) {
        if(key==null || !key.matches("tts/practice/[0-9]+/[a-f0-9]{64}\\.mp3") || signers.getIfAvailable()==null) throw new IllegalStateException("TTS_ASSET_UNAVAILABLE");
        var signed=signers.getObject().presignGetObject(GetObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(10))
            .getObjectRequest(GetObjectRequest.builder().bucket(properties.getBucket()).key(key).responseContentType("audio/mpeg").build()).build());
        return new Playback(signed.url().toString(),signed.expiration().atOffset(ZoneOffset.UTC));
    }
}
