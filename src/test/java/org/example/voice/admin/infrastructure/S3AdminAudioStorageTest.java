package org.example.voice.admin.infrastructure;

import org.example.voice.admin.domain.model.AdminAudioData.Upload;
import org.example.voice.common.exception.*;
import org.example.voice.practicecontent.domain.port.ReferenceAudioAssets;
import org.example.voice.training.infrastructure.storage.ObjectStorageProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.*;
import java.security.MessageDigest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class S3AdminAudioStorageTest {
    final S3Client client=mock(S3Client.class);
    final ReferenceAudioInspector inspector=mock(ReferenceAudioInspector.class);
    final ReferenceAudioAssets playback=mock(ReferenceAudioAssets.class);
    final ObjectStorageProperties properties=new ObjectStorageProperties();
    final byte[] bytes={1,2,3,4};
    S3AdminAudioStorageTest() { properties.setBucket("test-bucket");properties.setRegion("ap-northeast-2"); }
    S3AdminAudioStorage adapter(S3Presigner signer) { return new S3AdminAudioStorage(client,signer,properties,inspector,playback); }
    Upload upload(String sha) { return new Upload(UUID.randomUUID(),1,2,".m4a","audio/mp4",bytes.length,sha,"a".repeat(64),OffsetDateTime.now().plusMinutes(10),null,null); }
    String sha() throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    void source(Upload upload) {
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentType(upload.contentType()).contentLength(upload.size()).eTag("etag").build());
        when(client.getObject(any(GetObjectRequest.class))).thenAnswer(call -> new ResponseInputStream<>(GetObjectResponse.builder().build(),AbortableInputStream.create(new ByteArrayInputStream(bytes))));
    }
    @Test void uploadUrlSignsAnUnmodifiableActorIntentKeyChecksumAndCreateOnlyCondition() throws Exception {
        try(var signer=S3Presigner.builder().region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access","test-secret"))).build()) {
            var upload=upload(sha());var signed=adapter(signer).sign(upload);
            assertThat(signed.url()).contains("reference-audios/uploads/"+upload.id()+".m4a");
            var headers=new TreeMap<String,String>(String.CASE_INSENSITIVE_ORDER);headers.putAll(signed.headers());
            assertThat(headers).containsEntry("if-none-match","*").containsEntry("Content-Type","audio/mp4").doesNotContainKeys("Host","Content-Length");
            assertThat(signed.url()+headers).containsIgnoringCase("checksum");
        }
        verifyNoInteractions(client);
    }
    @Test void mismatchedBytesFailBeforeDecoderOrFinalObjectWrites() throws Exception {
        var upload=upload("f".repeat(64));source(upload);
        assertThatThrownBy(()->adapter(mock(S3Presigner.class)).verifyAndStore(upload))
                .isInstanceOfSatisfying(BaseException.class,e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_AUDIO_INVALID));
        verifyNoInteractions(inspector);verify(client,never()).putObject(any(PutObjectRequest.class),any(RequestBody.class));
    }
    @Test void validatedBytesAreWrittenToADifferentServerOnlyKeyAndNeverOverwriteIt() throws Exception {
        var upload=upload(sha());source(upload);when(inspector.validate(bytes,".m4a")).thenReturn(1500);
        var result=adapter(mock(S3Presigner.class)).verifyAndStore(upload);
        assertThat(result.objectKey()).isEqualTo("reference-audios/announcer/2/"+upload.id()+".m4a");
        verify(client).putObject(argThat((PutObjectRequest p) -> p.key().equals(result.objectKey()) && "*".equals(p.ifNoneMatch())
                && upload.sha256().equals(p.metadata().get("sha256"))),any(RequestBody.class));
        assertThat(result.durationMs()).isEqualTo(1500);
    }
    @Test void anExistingFinalObjectMustHaveExactlyTheVerifiedHashAndMetadata() throws Exception {
        var upload=upload(sha());source(upload);when(inspector.validate(bytes,".m4a")).thenReturn(1500);
        when(client.putObject(any(PutObjectRequest.class),any(RequestBody.class))).thenThrow(S3Exception.builder().statusCode(412).build());
        when(client.headObject(any(HeadObjectRequest.class))).thenAnswer(call -> {
            var request=call.getArgument(0,HeadObjectRequest.class);
            return HeadObjectResponse.builder().contentLength(upload.size()).contentType(upload.contentType()).eTag("etag")
                    .metadata(request.key().contains("/uploads/")?Map.of():Map.of("sha256","wrong")).build();
        });
        assertThatThrownBy(()->adapter(mock(S3Presigner.class)).verifyAndStore(upload))
                .isInstanceOfSatisfying(BaseException.class,e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_AUDIO_UNAVAILABLE));
    }
}
